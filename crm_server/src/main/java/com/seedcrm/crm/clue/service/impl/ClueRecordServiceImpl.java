package com.seedcrm.crm.clue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.clue.entity.ClueRecord;
import com.seedcrm.crm.clue.mapper.ClueRecordMapper;
import com.seedcrm.crm.clue.service.ClueRecordService;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClueRecordServiceImpl implements ClueRecordService {

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

        ClueRecord existing = clueRecordMapper.selectOne(Wrappers.<ClueRecord>lambdaQuery()
                .eq(ClueRecord::getClueId, record.getClueId())
                .eq(ClueRecord::getRecordKey, record.getRecordKey().trim())
                .last("LIMIT 1"));
        if (existing != null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        record.setRecordKey(record.getRecordKey().trim());
        record.setRecordType(StringUtils.hasText(record.getRecordType()) ? record.getRecordType().trim() : "CLUE");
        record.setTitle(StringUtils.hasText(record.getTitle()) ? record.getTitle().trim() : "客资同步");
        record.setContent(StringUtils.hasText(record.getContent()) ? record.getContent().trim() : null);
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
}
