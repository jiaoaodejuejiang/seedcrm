package com.seedcrm.crm.clue.service.impl;

import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.service.ClueService;
import com.seedcrm.crm.clue.service.DouyinClueSyncService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class DouyinClueSyncServiceImpl implements DouyinClueSyncService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ClueService clueService;

    public DouyinClueSyncServiceImpl(ClueService clueService) {
        this.clueService = clueService;
    }

    @Override
    public int syncIncremental() {
        LocalDateTime now = LocalDateTime.now();
        int imported = 0;
        for (int index = 0; index < 2; index++) {
            Clue clue = new Clue();
            String stamp = TIME_FORMATTER.format(now.minusSeconds(index * 5L));
            clue.setName("抖音客资-" + stamp + "-" + (index + 1));
            clue.setPhone(buildPhone(now, index));
            clue.setWechat("douyin_" + stamp + "_" + (index + 1));
            clue.setSourceChannel("DOUYIN");
            clue.setSource("douyin");
            clue.setRawData("{\"source\":\"douyin-api\",\"syncMode\":\"auto\",\"timestamp\":\"" + now + "\"}");
            clueService.addClue(clue);
            imported++;
        }
        return imported;
    }

    private String buildPhone(LocalDateTime now, int index) {
        String seed = TIME_FORMATTER.format(now) + index;
        String numeric = seed.substring(Math.max(0, seed.length() - 8));
        return "139" + String.format("%08d", Integer.parseInt(numeric));
    }
}
