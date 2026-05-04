package com.seedcrm.crm.clue.service;

import com.seedcrm.crm.clue.entity.ClueRecord;
import java.util.Collection;
import java.util.List;

public interface ClueRecordService {

    boolean addRecordIfAbsent(ClueRecord record);

    Long findClueIdByExternalIdentity(String sourceChannel, String externalRecordId, String externalOrderId);

    List<ClueRecord> listByClueIds(Collection<Long> clueIds);
}
