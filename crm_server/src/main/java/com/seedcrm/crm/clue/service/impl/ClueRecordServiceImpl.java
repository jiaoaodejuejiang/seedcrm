package com.seedcrm.crm.clue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.clue.entity.ClueRecord;
import com.seedcrm.crm.clue.mapper.ClueRecordMapper;
import com.seedcrm.crm.clue.service.ClueRecordService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClueRecordServiceImpl implements ClueRecordService {

    private static final int RECORD_KEY_MAX_LENGTH = 128;
    private static final int RECORD_TYPE_MAX_LENGTH = 32;
    private static final int SOURCE_CHANNEL_MAX_LENGTH = 32;
    private static final int EXTERNAL_ID_MAX_LENGTH = 128;
    private static final int TITLE_MAX_LENGTH = 128;
    private static final int CONTENT_MAX_LENGTH = 500;

    private final ClueRecordMapper clueRecordMapper;

    public ClueRecordServiceImpl(ClueRecordMapper clueRecordMapper) {
        this.clueRecordMapper = clueRecordMapper;
    }

    @Override
    @Transactional
    public boolean addRecordIfAbsent(ClueRecord record) {
        if (record == null || record.getClueId() == null || record.getClueId() <= 0) {
            throw new IllegalArgumentException("clueId is required");
        }
        if (!StringUtils.hasText(record.getRecordKey())) {
            throw new IllegalArgumentException("recordKey is required");
        }

        String recordKey = shortenWithHash(record.getRecordKey().trim(), RECORD_KEY_MAX_LENGTH);
        ClueRecord existing = clueRecordMapper.selectOne(Wrappers.<ClueRecord>lambdaQuery()
                .eq(ClueRecord::getClueId, record.getClueId())
                .eq(ClueRecord::getRecordKey, recordKey)
                .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        record.setRecordKey(recordKey);
        record.setRecordType(truncate(defaultText(record.getRecordType(), "CLUE"), RECORD_TYPE_MAX_LENGTH));
        record.setSourceChannel(truncate(record.getSourceChannel(), SOURCE_CHANNEL_MAX_LENGTH));
        record.setExternalRecordId(truncate(record.getExternalRecordId(), EXTERNAL_ID_MAX_LENGTH));
        record.setExternalOrderId(truncate(record.getExternalOrderId(), EXTERNAL_ID_MAX_LENGTH));
        record.setTitle(truncate(defaultText(record.getTitle(), "客资同步"), TITLE_MAX_LENGTH));
        record.setContent(truncate(record.getContent(), CONTENT_MAX_LENGTH));
        record.setOccurredAt(record.getOccurredAt() == null ? now : record.getOccurredAt());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            clueRecordMapper.insert(record);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public List<ClueRecord> listByClueIds(Collection<Long> clueIds) {
        List<Long> ids = clueIds == null
                ? List.of()
                : clueIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return clueRecordMapper.selectList(Wrappers.<ClueRecord>lambdaQuery()
                .in(ClueRecord::getClueId, ids)
                .orderByDesc(ClueRecord::getOccurredAt)
                .orderByDesc(ClueRecord::getId));
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String shortenWithHash(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        String suffix = ":" + sha256(value).substring(0, 24);
        int prefixLength = Math.max(1, maxLength - suffix.length());
        return value.substring(0, prefixLength) + suffix;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return String.valueOf(Math.abs(value.hashCode()));
        }
    }
}
