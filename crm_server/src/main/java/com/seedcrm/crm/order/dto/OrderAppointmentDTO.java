package com.seedcrm.crm.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class OrderAppointmentDTO {

    private Long orderId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appointmentTime;

    private Integer headcount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private List<LocalDateTime> appointmentSlots;

    private String storeName;

    private String previousStoreName;

    private String sourceSurface;

    private String appointmentReasonType;

    private String remark;
}
