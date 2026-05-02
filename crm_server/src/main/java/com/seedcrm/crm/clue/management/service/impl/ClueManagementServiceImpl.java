package com.seedcrm.crm.clue.management.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.AssignmentStrategyRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.AssignmentStrategyResponse;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigResponse;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DutyCustomerServiceBatchRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DutyCustomerServiceItemRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DutyCustomerServiceResponse;
import com.seedcrm.crm.clue.management.entity.ClueAssignmentStrategy;
import com.seedcrm.crm.clue.management.entity.DutyCustomerService;
import com.seedcrm.crm.clue.management.mapper.ClueAssignmentStrategyMapper;
import com.seedcrm.crm.clue.management.mapper.DutyCustomerServiceMapper;
import com.seedcrm.crm.clue.management.service.ClueManagementService;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClueManagementServiceImpl implements ClueManagementService {

    private static final long DEFAULT_STORE_ID = 10L;
    private static final String ROUND_ROBIN = "ROUND_ROBIN";
    public static final String CLUE_DEDUP_ENABLED_KEY = "clue.dedup.enabled";
    public static final String CLUE_DEDUP_WINDOW_DAYS_KEY = "clue.dedup.window_days";
    public static final int DEFAULT_DEDUP_WINDOW_DAYS = 90;

    private final ClueAssignmentStrategyMapper clueAssignmentStrategyMapper;
    private final DutyCustomerServiceMapper dutyCustomerServiceMapper;
    private final ClueMapper clueMapper;
    private final SystemConfigService systemConfigService;

    public ClueManagementServiceImpl(ClueAssignmentStrategyMapper clueAssignmentStrategyMapper,
                                     DutyCustomerServiceMapper dutyCustomerServiceMapper,
                                     ClueMapper clueMapper,
                                     SystemConfigService systemConfigService) {
        this.clueAssignmentStrategyMapper = clueAssignmentStrategyMapper;
        this.dutyCustomerServiceMapper = dutyCustomerServiceMapper;
        this.clueMapper = clueMapper;
        this.systemConfigService = systemConfigService;
    }

    @Override
    public AssignmentStrategyResponse getAssignmentStrategy(Long storeId) {
        return toAssignmentStrategyResponse(resolveStrategy(storeId));
    }

    @Override
    @Transactional
    public AssignmentStrategyResponse saveAssignmentStrategy(Long storeId, Long updatedBy, AssignmentStrategyRequest request) {
        Long finalStoreId = normalizeStoreId(storeId);
        String assignmentMode = normalizeAssignmentMode(request == null ? null : request.getAssignmentMode());
        Integer enabled = normalizeFlag(request == null ? null : request.getEnabled(), 1);

        ClueAssignmentStrategy strategy = findStrategy(finalStoreId);
        LocalDateTime now = LocalDateTime.now();
        if (strategy == null) {
            strategy = new ClueAssignmentStrategy();
            strategy.setStoreId(finalStoreId);
            strategy.setLastAssignedUserId(null);
            strategy.setEnabled(enabled);
            strategy.setAssignmentMode(assignmentMode);
            strategy.setUpdatedBy(updatedBy);
            strategy.setUpdatedAt(now);
            clueAssignmentStrategyMapper.insert(strategy);
        } else {
            strategy.setEnabled(enabled);
            strategy.setAssignmentMode(assignmentMode);
            strategy.setUpdatedBy(updatedBy);
            strategy.setUpdatedAt(now);
            clueAssignmentStrategyMapper.updateById(strategy);
        }
        return toAssignmentStrategyResponse(resolveStrategy(finalStoreId));
    }

    @Override
    public DedupConfigResponse getDedupConfig() {
        int enabled = systemConfigService.getBoolean(CLUE_DEDUP_ENABLED_KEY, true) ? 1 : 0;
        int windowDays = normalizeDedupWindowDays(systemConfigService.getString(
                CLUE_DEDUP_WINDOW_DAYS_KEY, String.valueOf(DEFAULT_DEDUP_WINDOW_DAYS)));
        LocalDateTime updatedAt = systemConfigService.listConfigs("clue.dedup.").stream()
                .map(SystemConfigDtos.ConfigResponse::getUpdateTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new DedupConfigResponse(enabled, windowDays, updatedAt);
    }

    @Override
    @Transactional
    public DedupConfigResponse saveDedupConfig(DedupConfigRequest request, PermissionRequestContext context) {
        int enabled = normalizeFlag(request == null ? null : request.getEnabled(), 1);
        int windowDays = normalizeDedupWindowDays(request == null ? null : request.getWindowDays());
        systemConfigService.saveConfig(configRequest(
                CLUE_DEDUP_ENABLED_KEY,
                enabled == 1 ? "true" : "false",
                "BOOLEAN",
                "客资入库启用按客户身份去重，默认开启",
                "更新客资去重开关"), context);
        systemConfigService.saveConfig(configRequest(
                CLUE_DEDUP_WINDOW_DAYS_KEY,
                String.valueOf(windowDays),
                "NUMBER",
                "客资去重窗口天数；窗口内同客户保留一条基础客资，多条订单/动作写入客资记录",
                "更新客资去重窗口"), context);
        return getDedupConfig();
    }

    @Override
    public List<DutyCustomerServiceResponse> listDutyCustomerServices(Long storeId) {
        return dutyCustomerServiceMapper.selectList(Wrappers.<DutyCustomerService>lambdaQuery()
                        .eq(DutyCustomerService::getStoreId, normalizeStoreId(storeId))
                        .orderByAsc(DutyCustomerService::getSortOrder)
                        .orderByAsc(DutyCustomerService::getId))
                .stream()
                .map(this::toDutyCustomerServiceResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<DutyCustomerServiceResponse> saveDutyCustomerServices(Long storeId, DutyCustomerServiceBatchRequest request) {
        Long finalStoreId = normalizeStoreId(storeId);
        List<DutyCustomerService> existingRows = dutyCustomerServiceMapper.selectList(Wrappers.<DutyCustomerService>lambdaQuery()
                .eq(DutyCustomerService::getStoreId, finalStoreId));
        Map<Long, DutyCustomerService> existingById = existingRows.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(DutyCustomerService::getId, Function.identity(), (left, right) -> left));
        Map<Long, DutyCustomerService> existingByUserId = existingRows.stream()
                .filter(item -> item.getUserId() != null)
                .collect(Collectors.toMap(DutyCustomerService::getUserId, Function.identity(), (left, right) -> left));

        List<DutyCustomerServiceItemRequest> submittedRows = request == null || request.getStaff() == null
                ? List.of()
                : request.getStaff();
        Set<Long> retainedIds = new HashSet<>();
        int fallbackSortOrder = 1;

        for (DutyCustomerServiceItemRequest submittedRow : submittedRows) {
            validateDutyCustomerService(submittedRow);
            DutyCustomerService entity = resolveDutyCustomerServiceEntity(existingById, existingByUserId, submittedRow);
            LocalDateTime now = LocalDateTime.now();
            entity.setStoreId(finalStoreId);
            entity.setUserId(submittedRow.getUserId());
            entity.setAccountName(defaultAccountName(submittedRow));
            entity.setUserName(submittedRow.getUserName().trim());
            entity.setShiftLabel(defaultShiftLabel(submittedRow));
            entity.setOnDuty(normalizeFlag(submittedRow.getOnDuty(), 1));
            entity.setOnLeave(normalizeFlag(submittedRow.getOnLeave(), 0));
            entity.setSortOrder(submittedRow.getSortOrder() == null ? fallbackSortOrder : submittedRow.getSortOrder());
            entity.setRemark(StringUtils.hasText(submittedRow.getRemark()) ? submittedRow.getRemark().trim() : null);
            entity.setUpdatedAt(now);
            if (entity.getId() == null) {
                entity.setCreatedAt(now);
                dutyCustomerServiceMapper.insert(entity);
            } else {
                dutyCustomerServiceMapper.updateById(entity);
            }
            retainedIds.add(entity.getId());
            fallbackSortOrder += 1;
        }

        for (DutyCustomerService existingRow : existingRows) {
            if (existingRow.getId() != null && !retainedIds.contains(existingRow.getId())) {
                dutyCustomerServiceMapper.deleteById(existingRow.getId());
            }
        }

        return listDutyCustomerServices(finalStoreId);
    }

    @Override
    @Transactional
    public Clue autoAssignIfEnabled(Clue clue) {
        if (clue == null || clue.getId() == null || clue.getCurrentOwnerId() != null) {
            return clue;
        }

        ClueAssignmentStrategy strategy = findStrategy(DEFAULT_STORE_ID);
        if (strategy == null || normalizeFlag(strategy.getEnabled(), 0) != 1) {
            return clue;
        }
        if (!ROUND_ROBIN.equals(normalize(strategy.getAssignmentMode()))) {
            return clue;
        }

        List<DutyCustomerService> availableStaff = dutyCustomerServiceMapper.selectList(Wrappers.<DutyCustomerService>lambdaQuery()
                        .eq(DutyCustomerService::getStoreId, DEFAULT_STORE_ID)
                        .eq(DutyCustomerService::getOnDuty, 1)
                        .eq(DutyCustomerService::getOnLeave, 0)
                        .orderByAsc(DutyCustomerService::getSortOrder)
                        .orderByAsc(DutyCustomerService::getId))
                .stream()
                .filter(item -> item.getUserId() != null)
                .toList();
        if (availableStaff.isEmpty()) {
            return clue;
        }

        DutyCustomerService selectedStaff = pickNextStaff(availableStaff, strategy.getLastAssignedUserId());
        LocalDateTime now = LocalDateTime.now();
        clue.setCurrentOwnerId(selectedStaff.getUserId());
        clue.setIsPublic(0);
        clue.setStatus("assigned");
        clue.setUpdatedAt(now);
        clueMapper.updateById(clue);

        strategy.setLastAssignedUserId(selectedStaff.getUserId());
        strategy.setUpdatedAt(now);
        clueAssignmentStrategyMapper.updateById(strategy);
        return clue;
    }

    private DutyCustomerService pickNextStaff(List<DutyCustomerService> availableStaff, Long lastAssignedUserId) {
        if (availableStaff.isEmpty()) {
            throw new BusinessException("no available duty customer service");
        }
        if (lastAssignedUserId == null) {
            return availableStaff.get(0);
        }
        for (int index = 0; index < availableStaff.size(); index += 1) {
            DutyCustomerService item = availableStaff.get(index);
            if (Objects.equals(item.getUserId(), lastAssignedUserId)) {
                return availableStaff.get((index + 1) % availableStaff.size());
            }
        }
        return availableStaff.get(0);
    }

    private void validateDutyCustomerService(DutyCustomerServiceItemRequest submittedRow) {
        if (submittedRow == null || submittedRow.getUserId() == null || submittedRow.getUserId() <= 0) {
            throw new BusinessException("duty customer service userId is required");
        }
        if (!StringUtils.hasText(submittedRow.getUserName())) {
            throw new BusinessException("duty customer service userName is required");
        }
    }

    private DutyCustomerService resolveDutyCustomerServiceEntity(Map<Long, DutyCustomerService> existingById,
                                                                 Map<Long, DutyCustomerService> existingByUserId,
                                                                 DutyCustomerServiceItemRequest submittedRow) {
        if (submittedRow.getId() != null) {
            DutyCustomerService existingByPrimaryId = existingById.get(submittedRow.getId());
            if (existingByPrimaryId != null) {
                return existingByPrimaryId;
            }
        }
        DutyCustomerService existingByBusinessKey = existingByUserId.get(submittedRow.getUserId());
        return existingByBusinessKey == null ? new DutyCustomerService() : existingByBusinessKey;
    }

    private AssignmentStrategyResponse toAssignmentStrategyResponse(ClueAssignmentStrategy strategy) {
        if (strategy == null) {
            return new AssignmentStrategyResponse(null, normalizeStoreId(null), 0, ROUND_ROBIN, null, null, null);
        }
        return new AssignmentStrategyResponse(
                strategy.getId(),
                strategy.getStoreId(),
                normalizeFlag(strategy.getEnabled(), 0),
                normalizeAssignmentMode(strategy.getAssignmentMode()),
                strategy.getLastAssignedUserId(),
                strategy.getUpdatedBy(),
                strategy.getUpdatedAt());
    }

    private DutyCustomerServiceResponse toDutyCustomerServiceResponse(DutyCustomerService entity) {
        return new DutyCustomerServiceResponse(
                entity.getId(),
                entity.getStoreId(),
                entity.getUserId(),
                entity.getAccountName(),
                entity.getUserName(),
                entity.getShiftLabel(),
                normalizeFlag(entity.getOnDuty(), 1),
                normalizeFlag(entity.getOnLeave(), 0),
                entity.getSortOrder(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ClueAssignmentStrategy resolveStrategy(Long storeId) {
        ClueAssignmentStrategy strategy = findStrategy(normalizeStoreId(storeId));
        if (strategy != null) {
            return strategy;
        }
        ClueAssignmentStrategy defaultStrategy = new ClueAssignmentStrategy();
        defaultStrategy.setStoreId(normalizeStoreId(storeId));
        defaultStrategy.setEnabled(0);
        defaultStrategy.setAssignmentMode(ROUND_ROBIN);
        return defaultStrategy;
    }

    private ClueAssignmentStrategy findStrategy(Long storeId) {
        return clueAssignmentStrategyMapper.selectOne(Wrappers.<ClueAssignmentStrategy>lambdaQuery()
                .eq(ClueAssignmentStrategy::getStoreId, normalizeStoreId(storeId))
                .last("LIMIT 1"));
    }

    private Long normalizeStoreId(Long storeId) {
        return storeId == null || storeId <= 0 ? DEFAULT_STORE_ID : storeId;
    }

    private String defaultAccountName(DutyCustomerServiceItemRequest submittedRow) {
        if (StringUtils.hasText(submittedRow.getAccountName())) {
            return submittedRow.getAccountName().trim();
        }
        return "cs_" + submittedRow.getUserId();
    }

    private String defaultShiftLabel(DutyCustomerServiceItemRequest submittedRow) {
        if (StringUtils.hasText(submittedRow.getShiftLabel())) {
            return submittedRow.getShiftLabel().trim();
        }
        return "默认班次";
    }

    private Integer normalizeFlag(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value == 1 ? 1 : 0;
    }

    private int normalizeDedupWindowDays(Integer value) {
        if (value == null) {
            return DEFAULT_DEDUP_WINDOW_DAYS;
        }
        return Math.max(1, Math.min(value, 3650));
    }

    private int normalizeDedupWindowDays(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_DEDUP_WINDOW_DAYS;
        }
        try {
            return normalizeDedupWindowDays(Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return DEFAULT_DEDUP_WINDOW_DAYS;
        }
    }

    private SystemConfigDtos.SaveConfigRequest configRequest(String key,
                                                             String value,
                                                             String valueType,
                                                             String description,
                                                             String summary) {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey(key);
        request.setConfigValue(value);
        request.setValueType(valueType);
        request.setScopeType("GLOBAL");
        request.setScopeId("GLOBAL");
        request.setEnabled(1);
        request.setDescription(description);
        request.setSummary(summary);
        return request;
    }

    private String normalizeAssignmentMode(String assignmentMode) {
        String normalizedMode = normalize(assignmentMode);
        if (!StringUtils.hasText(normalizedMode)) {
            return ROUND_ROBIN;
        }
        if (!ROUND_ROBIN.equals(normalizedMode)) {
            throw new BusinessException("only ROUND_ROBIN assignment mode is supported");
        }
        return normalizedMode;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
