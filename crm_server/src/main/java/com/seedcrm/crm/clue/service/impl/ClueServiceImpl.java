package com.seedcrm.crm.clue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.clue.entity.Clue;
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

    public ClueServiceImpl(ClueMapper clueMapper, DistributorService distributorService) {
        this.clueMapper = clueMapper;
        this.distributorService = distributorService;
    }

    @Override
    public Clue addClue(Clue clue) {
        if (clue == null) {
            throw new IllegalArgumentException("request body is required");
        }
        if (!StringUtils.hasText(clue.getPhone()) && !StringUtils.hasText(clue.getWechat())) {
            throw new IllegalArgumentException("phone or wechat is required");
        }

        Clue existingClue = findExistingClue(clue.getPhone(), clue.getWechat());
        if (existingClue != null) {
            return syncExistingClue(existingClue, clue);
        }

        LocalDateTime now = LocalDateTime.now();
        String sourceChannel = SourceChannel.resolveCode(clue.getSourceChannel(), clue.getSource());
        clue.setSourceChannel(sourceChannel);
        clue.setSource(SourceChannel.resolveLegacySource(sourceChannel, clue.getSource()));
        clue.setRawData(defaultRawData(clue.getRawData()));
        clue.setStatus("new");
        clue.setIsPublic(1);
        clue.setCurrentOwnerId(null);
        clue.setCreatedAt(now);
        clue.setUpdatedAt(now);
        clueMapper.insert(clue);
        return clue;
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

    private Clue findExistingClue(String phone, String wechat) {
        Clue phoneClue = null;
        Clue wechatClue = null;

        if (StringUtils.hasText(phone)) {
            phoneClue = clueMapper.selectOne(
                    Wrappers.<Clue>lambdaQuery().eq(Clue::getPhone, phone).last("LIMIT 1"));
        }
        if (StringUtils.hasText(wechat)) {
            wechatClue = clueMapper.selectOne(
                    Wrappers.<Clue>lambdaQuery().eq(Clue::getWechat, wechat).last("LIMIT 1"));
        }

        if (phoneClue != null && wechatClue != null && !phoneClue.getId().equals(wechatClue.getId())) {
            throw new IllegalArgumentException("phone and wechat already belong to different clues");
        }

        return phoneClue != null ? phoneClue : wechatClue;
    }

    private Clue syncExistingClue(Clue existingClue, Clue incomingClue) {
        boolean changed = false;
        if (!StringUtils.hasText(existingClue.getName()) && StringUtils.hasText(incomingClue.getName())) {
            existingClue.setName(incomingClue.getName().trim());
            changed = true;
        }
        if (!StringUtils.hasText(existingClue.getWechat()) && StringUtils.hasText(incomingClue.getWechat())) {
            existingClue.setWechat(incomingClue.getWechat().trim());
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
}
