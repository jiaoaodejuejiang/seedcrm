package com.seedcrm.crm.clue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileResponse;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileUpsertRequest;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.FollowRecordResponse;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.entity.ClueProfile;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.clue.mapper.ClueProfileMapper;
import com.seedcrm.crm.clue.service.ClueProfileService;
import com.seedcrm.crm.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClueProfileServiceImpl implements ClueProfileService {

    private static final int MAX_TAGS = 20;
    private static final int MAX_FOLLOW_RECORDS = 200;
    private static final Set<String> CALL_STATUS_VALUES = Set.of("NOT_CALLED", "CONNECTED", "MISSED", "CALLBACK", "INVALID");
    private static final Set<String> LEAD_STAGE_VALUES = Set.of(
            "NEW",
            "INTENT",
            "ARRIVED",
            "DEAL",
            "CALLBACK_PENDING",
            "WECHAT_ADDED",
            "DEPOSIT_PAID",
            "INVALID");
    private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<FollowRecordResponse>> FOLLOW_RECORD_LIST_TYPE = new TypeReference<>() {
    };

    private final ClueProfileMapper clueProfileMapper;
    private final ClueMapper clueMapper;
    private final ObjectMapper objectMapper;

    public ClueProfileServiceImpl(ClueProfileMapper clueProfileMapper,
                                  ClueMapper clueMapper,
                                  ObjectMapper objectMapper) {
        this.clueProfileMapper = clueProfileMapper;
        this.clueMapper = clueMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ClueProfileResponse saveProfile(ClueProfileUpsertRequest request, Long updatedBy) {
        if (request == null || request.getClueId() == null || request.getClueId() <= 0) {
            throw new BusinessException("clueId is required");
        }
        Clue clue = clueMapper.selectById(request.getClueId());
        if (clue == null) {
            throw new BusinessException("clue not found");
        }

        ClueProfile profile = findByClueId(request.getClueId());
        LocalDateTime now = LocalDateTime.now();
        if (profile == null) {
            profile = new ClueProfile();
            profile.setClueId(request.getClueId());
            profile.setCreatedAt(now);
        } else {
            assertNotStale(profile, request.getUpdatedAt());
        }
        apply(profile, request, updatedBy, now);
        if (profile.getId() == null) {
            try {
                clueProfileMapper.insert(profile);
            } catch (DuplicateKeyException exception) {
                profile = findByClueId(request.getClueId());
                if (profile == null) {
                    throw exception;
                }
                assertNotStale(profile, request.getUpdatedAt());
                apply(profile, request, updatedBy, now);
                clueProfileMapper.updateById(profile);
            }
        } else {
            clueProfileMapper.updateById(profile);
        }
        return toResponse(findByClueId(request.getClueId()));
    }

    @Override
    public List<ClueProfileResponse> listByClueIds(Collection<Long> clueIds) {
        List<Long> ids = clueIds == null
                ? List.of()
                : clueIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return clueProfileMapper.selectList(Wrappers.<ClueProfile>lambdaQuery()
                        .in(ClueProfile::getClueId, ids))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void apply(ClueProfile profile, ClueProfileUpsertRequest request, Long updatedBy, LocalDateTime now) {
        profile.setDisplayName(limit(clean(request.getDisplayName()), 64));
        profile.setPhone(limit(clean(request.getPhone()), 32));
        profile.setCallStatus(normalizeEnum(request.getCallStatus(), CALL_STATUS_VALUES, "callStatus"));
        profile.setLeadStage(normalizeEnum(request.getLeadStage(), LEAD_STAGE_VALUES, "leadStage"));
        profile.setLeadTagsJson(writeJson(normalizeTags(request.getLeadTags())));
        profile.setFollowRecordsJson(writeJson(normalizeFollowRecords(request.getFollowRecords(), now)));
        profile.setIntendedStoreName(limit(clean(request.getIntendedStoreName()), 128));
        profile.setAssignedAt(request.getAssignedAt());
        profile.setUpdatedBy(updatedBy);
        profile.setUpdatedAt(now);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = limit(clean(tag), 32);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
            if (normalized.size() >= MAX_TAGS) {
                break;
            }
        }
        return List.copyOf(normalized);
    }

    private List<FollowRecordResponse> normalizeFollowRecords(List<FollowRecordResponse> records, LocalDateTime now) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .filter(Objects::nonNull)
                .map(record -> new FollowRecordResponse(
                        record.getId(),
                        limit(clean(record.getContent()), 500),
                        record.getCreatedAt() == null ? now : record.getCreatedAt()))
                .filter(record -> StringUtils.hasText(record.getContent()))
                .limit(MAX_FOLLOW_RECORDS)
                .toList();
    }

    private String normalizeEnum(String value, Set<String> allowedValues, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new BusinessException(fieldName + " is invalid");
        }
        return normalized;
    }

    private ClueProfile findByClueId(Long clueId) {
        return clueProfileMapper.selectOne(Wrappers.<ClueProfile>lambdaQuery()
                .eq(ClueProfile::getClueId, clueId)
                .last("LIMIT 1"));
    }

    private void assertNotStale(ClueProfile profile, LocalDateTime requestUpdatedAt) {
        if (profile == null || profile.getUpdatedAt() == null || requestUpdatedAt == null) {
            return;
        }
        LocalDateTime existing = profile.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime requested = requestUpdatedAt.truncatedTo(ChronoUnit.SECONDS);
        if (!existing.equals(requested)) {
            throw new BusinessException("clue profile has been updated, please refresh and retry");
        }
    }

    private ClueProfileResponse toResponse(ClueProfile profile) {
        if (profile == null) {
            return null;
        }
        return new ClueProfileResponse(
                profile.getId(),
                profile.getClueId(),
                profile.getDisplayName(),
                profile.getPhone(),
                profile.getCallStatus(),
                profile.getLeadStage(),
                readList(profile.getLeadTagsJson(), TAG_LIST_TYPE),
                readList(profile.getFollowRecordsJson(), FOLLOW_RECORD_LIST_TYPE),
                profile.getIntendedStoreName(),
                profile.getAssignedAt(),
                profile.getUpdatedBy(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private <T> List<T> readList(String value, TypeReference<List<T>> typeReference) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            List<T> parsed = objectMapper.readValue(value, typeReference);
            return parsed == null ? List.of() : parsed;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception exception) {
            throw new BusinessException("failed to serialize clue profile");
        }
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
