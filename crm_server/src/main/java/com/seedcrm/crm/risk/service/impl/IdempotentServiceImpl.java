package com.seedcrm.crm.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.risk.entity.IdempotentRecord;
import com.seedcrm.crm.risk.enums.IdempotentBizType;
import com.seedcrm.crm.risk.enums.IdempotentStatus;
import com.seedcrm.crm.risk.mapper.IdempotentRecordMapper;
import com.seedcrm.crm.risk.service.IdempotentService;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class IdempotentServiceImpl implements IdempotentService {

    private final IdempotentRecordMapper idempotentRecordMapper;

    public IdempotentServiceImpl(IdempotentRecordMapper idempotentRecordMapper) {
        this.idempotentRecordMapper = idempotentRecordMapper;
    }

    @Override
    public boolean tryStart(String bizKey, IdempotentBizType bizType) {
        if (bizType == null) {
            throw new BusinessException("idempotent bizType is required");
        }
        String normalizedBizKey = requireBizKey(bizKey);

        IdempotentRecord record = new IdempotentRecord();
        record.setBizKey(normalizedBizKey);
        record.setBizType(bizType.name());
        record.setStatus(IdempotentStatus.PROCESSING.name());
        record.setCreateTime(LocalDateTime.now());
        try {
            return idempotentRecordMapper.insert(record) > 0;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public void markSuccess(String bizKey) {
        updateStatus(requireBizKey(bizKey), IdempotentStatus.SUCCESS);
    }

    @Override
    public void markFail(String bizKey) {
        updateStatus(requireBizKey(bizKey), IdempotentStatus.FAIL);
    }

    @Override
    public IdempotentRecord getByBizKey(String bizKey) {
        return idempotentRecordMapper.selectByBizKey(requireBizKey(bizKey));
    }

    private void updateStatus(String bizKey, IdempotentStatus status) {
        idempotentRecordMapper.update(null, new LambdaUpdateWrapper<IdempotentRecord>()
                .eq(IdempotentRecord::getBizKey, bizKey)
                .set(IdempotentRecord::getStatus, status.name()));
    }

    private String requireBizKey(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("idempotent bizKey is required");
        }
        return bizKey.trim();
    }
}
