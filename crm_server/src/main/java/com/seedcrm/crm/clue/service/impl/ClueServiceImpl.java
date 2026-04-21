package com.seedcrm.crm.clue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.clue.service.ClueService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClueServiceImpl extends ServiceImpl<ClueMapper, Clue> implements ClueService {

    private final ClueMapper clueMapper;

    public ClueServiceImpl(ClueMapper clueMapper) {
        this.clueMapper = clueMapper;
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
            return existingClue;
        }

        LocalDateTime now = LocalDateTime.now();
        clue.setSource(StringUtils.hasText(clue.getSource()) ? clue.getSource() : "douyin");
        clue.setStatus("new");
        clue.setIsPublic(1);
        clue.setCurrentOwnerId(null);
        clue.setCreatedAt(now);
        clue.setUpdatedAt(now);
        clueMapper.insert(clue);
        return clue;
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
}
