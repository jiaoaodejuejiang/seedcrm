package com.seedcrm.crm.clue.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.seedcrm.crm.clue.entity.Clue;
import java.util.List;

public interface ClueService extends IService<Clue> {

    Clue addClue(Clue clue);

    Clue createDistributorClue(Long distributorId, String phone, String name);

    IPage<Clue> pageClues(long page, long size, boolean publicOnly);

    Clue assignClue(Long clueId, Long userId);

    Clue recycleClue(Long clueId);

    List<Clue> listPublicClues();
}
