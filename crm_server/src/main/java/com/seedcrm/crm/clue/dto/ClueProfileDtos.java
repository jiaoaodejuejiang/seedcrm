package com.seedcrm.crm.clue.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class ClueProfileDtos {

    private ClueProfileDtos() {
    }

    @Data
    @NoArgsConstructor
    public static class ClueProfileUpsertRequest {
        private Long clueId;
        private String displayName;
        private String phone;
        private String callStatus;
        private String leadStage;
        private List<String> leadTags = new ArrayList<>();
        private List<FollowRecordResponse> followRecords = new ArrayList<>();
        private String intendedStoreName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime assignedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClueProfileResponse {
        private Long id;
        private Long clueId;
        private String displayName;
        private String phone;
        private String callStatus;
        private String leadStage;
        private List<String> leadTags;
        private List<FollowRecordResponse> followRecords;
        private String intendedStoreName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime assignedAt;
        private Long updatedBy;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowRecordResponse {
        private Long id;
        private String content;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
