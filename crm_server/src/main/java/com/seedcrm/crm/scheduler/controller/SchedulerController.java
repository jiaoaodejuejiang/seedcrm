package com.seedcrm.crm.scheduler.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerJobUpsertRequest;
import com.seedcrm.crm.scheduler.dto.SchedulerTriggerRequest;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scheduler")
public class SchedulerController {

    private final SchedulerService schedulerService;

    public SchedulerController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @GetMapping("/jobs")
    public ApiResponse<List<SchedulerJob>> listJobs() {
        return ApiResponse.success(schedulerService.listJobs());
    }

    @GetMapping("/logs")
    public ApiResponse<List<SchedulerJobLog>> listLogs(@RequestParam(required = false) String jobCode) {
        return ApiResponse.success(schedulerService.listLogs(jobCode));
    }

    @PostMapping("/job/save")
    public ApiResponse<SchedulerJob> saveJob(@RequestBody SchedulerJobUpsertRequest request) {
        return ApiResponse.success(schedulerService.saveJob(request));
    }

    @PostMapping("/trigger")
    public ApiResponse<SchedulerJobLog> trigger(@RequestBody SchedulerTriggerRequest request) {
        return ApiResponse.success(schedulerService.trigger(request));
    }

    @PostMapping("/retry")
    public ApiResponse<List<SchedulerJobLog>> retry(@RequestParam String jobCode) {
        return ApiResponse.success(schedulerService.retryFailed(jobCode));
    }
}
