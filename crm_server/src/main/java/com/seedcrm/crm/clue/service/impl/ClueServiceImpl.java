package com.seedcrm.crm.clue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigResponse;
import com.seedcrm.crm.clue.management.service.ClueManagementService;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.clue.service.ClueService;
import com.seedcrm.crm.distributor.service.DistributorService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClueServiceImpl extends ServiceImpl<ClueMapper, Clue> implements ClueService {

    private final ClueMapper clueMapper;
    private final DistributorService distributorService;
    private final ClueManagementService clueManagementService;

    public ClueServiceImpl(ClueMapper clueMapper,
                           DistributorService distributorService,
                           ClueManagementService clueManagementService) {
        this.clueMapper = clueMapper;
        this.distributorService = distributorService;
        this.clueManagementService = clueManagementService;
    }

    @Override
    public Clue addClue(Clue clue) {
        if (clue == null) {
            throw new IllegalArgumentException("request body is required");
        }
        if (!StringUtils.hasText(clue.getPhone()) && !StringUtils.hasText(clue.getWechat())) {
            throw new IllegalArgumentException("phone or wechat is required");
        }

        String sourceChannel = SourceChannel.resolveCode(clue.getSourceChannel(), clue.getSource());
        clue.setPhone(normalizePhone(clue.getPhone()));
        clue.setWechat(normalizeWechat(clue.getWechat()));
        if (!StringUtils.hasText(clue.getPhone()) && !StringUtils.hasText(clue.getWechat())) {
            throw new IllegalArgumentException("phone or wechat is required");
        }
        clue.setSourceChannel(sourceChannel);
        clue.setSource(SourceChannel.resolveLegacySource(sourceChannel, clue.getSource()));

        DedupConfigResponse dedupConfig = resolveDedupConfig();
        Clue existingClue = findExistingClue(clue.getPhone(), clue.getWechat(), sourceChannel, dedupConfig);
        if (existingClue != null) {
            return syncExistingClue(existingClue, clue);
        }

        LocalDateTime now = LocalDateTime.now();
        clue.setRawData(defaultRawData(clue.getRawData()));
        clue.setStatus("new");
        clue.setIsPublic(1);
        clue.setCurrentOwnerId(null);
        clue.setCreatedAt(now);
        clue.setUpdatedAt(now);
        clueMapper.insert(clue);
        return autoAssignClue(clue);
    }

    @Override
    public Clue createDistributorClue(Long distributorId, String phone, String name) {
        if (distributorId == null || distributorId <= 0) {
            throw new IllegalArgumentException("distributorId is required");
        }
        distributorService.getByIdOrThrow(distributorId);

        Clue clue = new Clue();
        clue.setPhone(phone);
        clue.setName(name);
        clue.setSourceChannel(SourceChannel.DISTRIBUTOR.name());
        clue.setSourceId(distributorId);
        clue.setSource("distribution");
        clue.setRawData("{\"source\":\"distribution\"}");
        return addClue(clue);
    }

    @Override
    public IPage<Clue> pageClues(long page, long size, boolean publicOnly) {
        Page<Clue> cluePage = new Page<>(page, size);
        LambdaQueryWrapper<Clue> queryWrapper = Wrappers.lambdaQuery();
        if (publicOnly) {
            queryWrapper.eq(Clue::getIsPublic, 1);
        }
        queryWrapper.orderByDesc(Clue::getCreatedAt).orderByDesc(Clue::getId);
        return clueMapper.selectPage(cluePage, queryWrapper);
    }

    @Override
    public Clue assignClue(Long clueId, Long userId) {
        if (clueId == null || userId == null) {
            throw new IllegalArgumentException("clueId and userId are required");
        }

        Clue clue = clueMapper.selectById(clueId);
        if (clue == null) {
            throw new NoSuchElementException("clue not found");
        }

        clue.setCurrentOwnerId(userId);
        clue.setIsPublic(0);
        clue.setStatus("assigned");
        clue.setUpdatedAt(LocalDateTime.now());
        clueMapper.updateById(clue);
        return clue;
    }

    @Override
    public Clue recycleClue(Long clueId) {
        if (clueId == null || clueId <= 0) {
            throw new IllegalArgumentException("clueId is required");
        }
        Clue clue = clueMapper.selectById(clueId);
        if (clue == null) {
            throw new NoSuchElementException("clue not found");
        }
        clue.setCurrentOwnerId(null);
        clue.setIsPublic(1);
        clue.setStatus("new");
        clue.setUpdatedAt(LocalDateTime.now());
        clueMapper.updateById(clue);
        return clue;
    }

    @Override
    public List<Clue> listPublicClues() {
        LambdaQueryWrapper<Clue> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(Clue::getIsPublic, 1)
                .orderByDesc(Clue::getCreatedAt)
                .orderByDesc(Clue::getId);
        return clueMapper.selectList(queryWrapper);
    }

    @Override
    public Clue autoAssignClue(Clue clue) {
        return clueManagementService.autoAssignIfEnabled(clue);
    }

    private Clue findExistingClue(String phone, String wechat, String sourceChannel, DedupConfigResponse dedupConfig) {
        if (dedupConfig != null && dedupConfig.getEnabled() != null && dedupConfig.getEnabled() == 1) {
            Clue windowMatchedClue = findExistingClueByIdentity(
                    phone,
                    wechat,
                    sourceChannel,
                    LocalDateTime.now().minusDays(resolveDedupWindowDays(dedupConfig)));
            if (windowMatchedClue != null) {
                return windowMatchedClue;
            }
        }
        // The database still protects phone/wechat as customer identity. This fallback preserves old behavior
        // and prevents duplicate-key failures when a historical customer comes back outside the configured window.
        return findExistingClueByIdentity(phone, wechat, null, null);
    }

    private Clue findExistingClueByIdentity(String phone, String wechat, String sourceChannel, LocalDateTime dedupThreshold) {
        Clue phoneClue = null;
        Clue wechatClue = null;

        if (StringUtils.hasText(phone)) {
            LambdaQueryWrapper<Clue> query = Wrappers.<Clue>lambdaQuery()
                    .eq(Clue::getPhone, normalizePhone(phone));
            if (StringUtils.hasText(sourceChannel)) {
                query.eq(Clue::getSourceChannel, sourceChannel);
            }
            if (dedupThreshold != null) {
                query.ge(Clue::getCreatedAt, dedupThreshold);
            }
            phoneClue = clueMapper.selectOne(query
                    .orderByDesc(Clue::getCreatedAt)
                    .orderByDesc(Clue::getId)
                    .last("LIMIT 1"));
        }
        if (StringUtils.hasText(wechat)) {
            LambdaQueryWrapper<Clue> query = Wrappers.<Clue>lambdaQuery()
                    .eq(Clue::getWechat, normalizeWechat(wechat));
            if (StringUtils.hasText(sourceChannel)) {
                query.eq(Clue::getSourceChannel, sourceChannel);
            }
            if (dedupThreshold != null) {
                query.ge(Clue::getCreatedAt, dedupThreshold);
            }
            wechatClue = clueMapper.selectOne(query
                    .orderByDesc(Clue::getCreatedAt)
                    .orderByDesc(Clue::getId)
                    .last("LIMIT 1"));
        }

        if (phoneClue != null && wechatClue != null && !phoneClue.getId().equals(wechatClue.getId())) {
            throw new IllegalArgumentException("phone and wechat already belong to different clues");
        }

        return phoneClue != null ? phoneClue : wechatClue;
    }

    private DedupConfigResponse resolveDedupConfig() {
        try {
            DedupConfigResponse config = clueManagementService.getDedupConfig();
            if (config != null) {
                return config;
            }
        } catch (RuntimeException ignored) {
            // Keep clue intake available if the optional config lookup is temporarily unavailable.
        }
        return new DedupConfigResponse(1, 90, null);
    }

    private int resolveDedupWindowDays(DedupConfigResponse dedupConfig) {
        Integer windowDays = dedupConfig == null ? null : dedupConfig.getWindowDays();
        if (windowDays == null) {
            return 90;
        }
        return Math.max(1, Math.min(windowDays, 3650));
    }

    private Clue syncExistingClue(Clue existingClue, Clue incomingClue) {
        boolean changed = false;
        if (!StringUtils.hasText(existingClue.getName()) && StringUtils.hasText(incomingClue.getName())) {
            existingClue.setName(incomingClue.getName().trim());
            changed = true;
        }
        if (!StringUtils.hasText(existingClue.getWechat()) && StringUtils.hasText(incomingClue.getWechat())) {
            existingClue.setWechat(normalizeWechat(incomingClue.getWechat()));
            changed = true;
        }
        if (!StringUtils.hasText(existingClue.getSourceChannel()) && StringUtils.hasText(incomingClue.getSourceChannel())) {
            existingClue.setSourceChannel(SourceChannel.resolveCode(incomingClue.getSourceChannel(), incomingClue.getSource()));
            changed = true;
        }
        if (!StringUtils.hasText(existingClue.getSource()) && StringUtils.hasText(incomingClue.getSource())) {
            existingClue.setSource(SourceChannel.resolveLegacySource(incomingClue.getSourceChannel(), incomingClue.getSource()));
            changed = true;
        }
        if (existingClue.getSourceId() == null && incomingClue.getSourceId() != null) {
            existingClue.setSourceId(incomingClue.getSourceId());
            changed = true;
        }
        if (!StringUtils.hasText(existingClue.getRawData()) && StringUtils.hasText(incomingClue.getRawData())) {
            existingClue.setRawData(incomingClue.getRawData().trim());
            changed = true;
        }
        if (changed) {
            existingClue.setUpdatedAt(LocalDateTime.now());
            clueMapper.updateById(existingClue);
        }
        return existingClue;
    }

    private String defaultRawData(String rawData) {
        return StringUtils.hasText(rawData) ? rawData.trim() : "{}";
    }

    private String normalizePhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim()
                .replaceAll("[\\s\\-()（）]", "")
                .replaceAll("^\\+86", "")
                .replaceAll("^0086", "");
        String digits = normalized.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private String normalizeWechat(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }
}
