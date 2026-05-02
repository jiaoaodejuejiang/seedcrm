package com.seedcrm.crm.clue.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileResponse;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileUpsertRequest;
import com.seedcrm.crm.clue.dto.DistributorClueCreateRequest;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.service.ClueProfileService;
import com.seedcrm.crm.clue.service.ClueService;
import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.CluePermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/clue")
public class ClueController {

    private final ClueService clueService;
    private final ClueProfileService clueProfileService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final CluePermissionGuard cluePermissionGuard;

    public ClueController(ClueService clueService,
                          ClueProfileService clueProfileService,
                          PermissionRequestContextResolver permissionRequestContextResolver,
                          CluePermissionGuard cluePermissionGuard) {
        this.clueService = clueService;
        this.clueProfileService = clueProfileService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.cluePermissionGuard = cluePermissionGuard;
    }

    @PostMapping("/add")
    public Clue add(@RequestBody(required = false) Clue clue, HttpServletRequest request) {
        try {
            PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
            cluePermissionGuard.checkCreate(context);
            return clueService.addClue(clue);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/distributor/add")
    public Clue addDistributorClue(@RequestBody(required = false) DistributorClueCreateRequest request,
                                   HttpServletRequest httpServletRequest) {
        throw legacyDistributionClueEndpointDisabled();
    }

    @PostMapping("/distribution/add")
    public Clue addDistributionClue(@RequestBody(required = false) DistributorClueCreateRequest request,
                                    HttpServletRequest httpServletRequest) {
        throw legacyDistributionClueEndpointDisabled();
    }

    @GetMapping("/list")
    public IPage<Clue> list(@RequestParam(defaultValue = "1") long page,
                            @RequestParam(defaultValue = "10") long size,
                            @RequestParam(defaultValue = "false") boolean publicOnly,
                            HttpServletRequest request) {
        if (page < 1 || size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page and size must be greater than 0");
        }
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        cluePermissionGuard.checkView(context, null);
        return clueService.pageClues(page, size, publicOnly);
    }

    @PostMapping("/assign")
    public Clue assign(@RequestBody(required = false) Map<String, Long> requestBody,
                       @RequestParam(required = false) Long clueId,
                       @RequestParam(required = false) Long userId,
                       HttpServletRequest request) {
        Long finalClueId = clueId;
        Long finalUserId = userId;
        if (requestBody != null) {
            if (finalClueId == null) {
                finalClueId = requestBody.get("clueId");
            }
            if (finalUserId == null) {
                finalUserId = requestBody.get("userId");
            }
        }
        try {
            PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
            cluePermissionGuard.checkAssign(context, finalClueId);
            return clueService.assignClue(finalClueId, finalUserId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/public")
    public List<Clue> publicList(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        cluePermissionGuard.checkView(context, null);
        return clueService.listPublicClues();
    }

    @PostMapping("/recycle")
    public Clue recycle(@RequestParam Long clueId, HttpServletRequest request) {
        try {
            PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
            cluePermissionGuard.checkRecycle(context, clueId);
            return clueService.recycleClue(clueId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PostMapping("/profile")
    public ApiResponse<ClueProfileResponse> saveProfile(@RequestBody(required = false) ClueProfileUpsertRequest request,
                                                        HttpServletRequest httpServletRequest) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(httpServletRequest);
        cluePermissionGuard.checkUpdate(context, request == null ? null : request.getClueId());
        return ApiResponse.success(clueProfileService.saveProfile(request, context.getCurrentUserId()));
    }

    @GetMapping("/test")
    public String test() {
        return "clue module ok";
    }

    private ResponseStatusException legacyDistributionClueEndpointDisabled() {
        return new ResponseStatusException(HttpStatus.GONE,
                "分销成交已切换为方案B：已支付订单只能通过 /open/distribution/events 入站并直接进入 Customer + Order，不再创建 Clue。");
    }
}
