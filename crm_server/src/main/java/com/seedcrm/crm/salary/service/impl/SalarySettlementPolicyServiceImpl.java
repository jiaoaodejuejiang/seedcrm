package com.seedcrm.crm.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.salary.dto.SalarySettlementPolicyDtos;
import com.seedcrm.crm.salary.entity.SalarySettlementPolicy;
import com.seedcrm.crm.salary.mapper.SalarySettlementPolicyMapper;
import com.seedcrm.crm.salary.service.SalarySettlementPolicyService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SalarySettlementPolicyServiceImpl implements SalarySettlementPolicyService {

    private static final String SUBJECT_INTERNAL_STAFF = "INTERNAL_STAFF";
    private static final String SUBJECT_DISTRIBUTOR = "DISTRIBUTOR";
    private static final String SCOPE_ROLE = "ROLE";
    private static final String SCOPE_AMOUNT = "AMOUNT";
    private static final String CYCLE_MONTHLY = "MONTHLY";
    private static final String CYCLE_INSTANT = "INSTANT";
    private static final String MODE_LEDGER_ONLY = "LEDGER_ONLY";
    private static final String MODE_WITHDRAW_DIRECT = "WITHDRAW_DIRECT";
    private static final String MODE_WITHDRAW_AUDIT = "WITHDRAW_AUDIT";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final SalarySettlementPolicyMapper policyMapper;
    private final JdbcTemplate jdbcTemplate;

    public SalarySettlementPolicyServiceImpl(SalarySettlementPolicyMapper policyMapper, JdbcTemplate jdbcTemplate) {
        this.policyMapper = policyMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SalarySettlementPolicyDtos.PolicyResponse> listPolicies() {
        return policyMapper.selectList(new LambdaQueryWrapper<SalarySettlementPolicy>()
                        .orderByAsc(SalarySettlementPolicy::getPriority)
                        .orderByAsc(SalarySettlementPolicy::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SalarySettlementPolicyDtos.PolicyResponse saveDraft(SalarySettlementPolicyDtos.SavePolicyRequest request,
                                                               PermissionRequestContext context) {
        validateSaveRequest(request);
        LocalDateTime now = LocalDateTime.now();
        SalarySettlementPolicy existing = request.getId() == null ? null : policyMapper.selectById(request.getId());
        SalarySettlementPolicy policy;
        if (existing == null) {
            policy = new SalarySettlementPolicy();
            policy.setCreateTime(now);
            policy.setEnabled(0);
            policy.setStatus(STATUS_DRAFT);
        } else if (STATUS_PUBLISHED.equalsIgnoreCase(existing.getStatus())) {
            policy = new SalarySettlementPolicy();
            policy.setSourcePolicyId(existing.getId());
            policy.setCreateTime(now);
            policy.setEnabled(0);
            policy.setStatus(STATUS_DRAFT);
        } else {
            policy = existing;
        }
        applyRequest(policy, request);
        policy.setUpdateTime(now);
        if (policy.getId() == null) {
            if (policyMapper.insert(policy) <= 0) {
                throw new BusinessException("保存结算规则草稿失败");
            }
        } else if (policyMapper.updateById(policy) <= 0) {
            throw new BusinessException("更新结算规则草稿失败");
        }
        SalarySettlementPolicy savedPolicy = policyMapper.selectById(policy.getId());
        audit(savedPolicy, "SAVE_DRAFT", context, "保存结算/线下处理登记规则草稿");
        return toResponse(savedPolicy);
    }

    @Override
    @Transactional
    public SalarySettlementPolicyDtos.PolicyResponse publish(SalarySettlementPolicyDtos.PolicyStatusRequest request,
                                                             PermissionRequestContext context) {
        Long policyId = request == null ? null : request.getPolicyId();
        SalarySettlementPolicy policy = getPolicy(policyId);
        if (STATUS_DISABLED.equalsIgnoreCase(policy.getStatus()) || STATUS_ARCHIVED.equalsIgnoreCase(policy.getStatus())) {
            throw new BusinessException("已停用或已归档规则不能发布");
        }
        validatePublishConflicts(policy);
        LocalDateTime now = LocalDateTime.now();
        if (policy.getSourcePolicyId() != null) {
            SalarySettlementPolicy source = policyMapper.selectById(policy.getSourcePolicyId());
            if (source != null && STATUS_PUBLISHED.equalsIgnoreCase(source.getStatus())) {
                source.setStatus(STATUS_ARCHIVED);
                source.setEnabled(0);
                source.setUpdateTime(now);
                policyMapper.updateById(source);
            }
        }
        policy.setStatus(STATUS_PUBLISHED);
        policy.setEnabled(1);
        policy.setPublishedTime(now);
        policy.setUpdateTime(now);
        if (policyMapper.updateById(policy) <= 0) {
            throw new BusinessException("发布结算规则失败");
        }
        audit(policy, "PUBLISH", context, "发布结算/线下处理登记规则");
        return toResponse(policy);
    }

    @Override
    @Transactional
    public SalarySettlementPolicyDtos.PolicyResponse disable(SalarySettlementPolicyDtos.PolicyStatusRequest request,
                                                             PermissionRequestContext context) {
        SalarySettlementPolicy policy = getPolicy(request == null ? null : request.getPolicyId());
        policy.setEnabled(0);
        policy.setStatus(STATUS_DISABLED);
        policy.setUpdateTime(LocalDateTime.now());
        if (policyMapper.updateById(policy) <= 0) {
            throw new BusinessException("停用结算规则失败");
        }
        audit(policy, "DISABLE", context, "停用结算/线下处理登记规则");
        return toResponse(policy);
    }

    @Override
    public SalarySettlementPolicyDtos.SimulateResponse simulate(SalarySettlementPolicyDtos.SimulateRequest request) {
        String subjectType = normalizeOrDefault(request == null ? null : request.getSubjectType(), SUBJECT_INTERNAL_STAFF);
        String roleCode = normalize(request == null ? null : request.getRoleCode());
        BigDecimal amount = normalizeAmount(request == null ? null : request.getAmount());
        SalarySettlementPolicy matchedPolicy = matchPublishedPolicy(subjectType, roleCode, amount);

        SalarySettlementPolicyDtos.SimulateResponse response = new SalarySettlementPolicyDtos.SimulateResponse();
        response.setSubjectType(subjectType);
        response.setRoleCode(roleCode);
        response.setAmount(amount);
        response.setMatched(matchedPolicy != null);
        if (matchedPolicy == null) {
            response.setSettlementCycle(CYCLE_MONTHLY);
            response.setSettlementMode(MODE_LEDGER_ONLY);
            response.setLedgerOnly(true);
            response.setMessage("未命中已发布规则；为资金安全，默认按只记账处理");
            response.setNextAction("补充并发布结算/线下处理登记规则");
            return response;
        }
        response.setMatchedPolicy(toResponse(matchedPolicy));
        response.setSettlementCycle(matchedPolicy.getSettlementCycle());
        response.setSettlementMode(matchedPolicy.getSettlementMode());
        response.setLedgerOnly(MODE_LEDGER_ONLY.equalsIgnoreCase(matchedPolicy.getSettlementMode()));
        response.setAutoApprove(MODE_WITHDRAW_DIRECT.equalsIgnoreCase(matchedPolicy.getSettlementMode()));
        response.setRequiresAudit(MODE_WITHDRAW_AUDIT.equalsIgnoreCase(matchedPolicy.getSettlementMode()));
        response.setNextAction(resolveNextAction(matchedPolicy));
        response.setMessage("模拟匹配成功；本接口不创建结算单、不创建线下处理登记单、不写入资金流水");
        return response;
    }

    private SalarySettlementPolicy matchPublishedPolicy(String subjectType, String roleCode, BigDecimal amount) {
        return policyMapper.selectList(new LambdaQueryWrapper<SalarySettlementPolicy>()
                        .eq(SalarySettlementPolicy::getStatus, STATUS_PUBLISHED)
                        .eq(SalarySettlementPolicy::getEnabled, 1)
                        .eq(SalarySettlementPolicy::getSubjectType, subjectType)
                        .orderByAsc(SalarySettlementPolicy::getPriority)
                        .orderByAsc(SalarySettlementPolicy::getId))
                .stream()
                .filter(policy -> matches(policy, roleCode, amount))
                .findFirst()
                .orElse(null);
    }

    private boolean matches(SalarySettlementPolicy policy, String roleCode, BigDecimal amount) {
        String scopeType = normalize(policy.getScopeType());
        if (SCOPE_ROLE.equals(scopeType)) {
            return roleCodes(policy.getRoleCodes()).contains(roleCode);
        }
        if (SCOPE_AMOUNT.equals(scopeType)) {
            BigDecimal min = policy.getAmountMin() == null ? BigDecimal.ZERO : policy.getAmountMin();
            BigDecimal max = policy.getAmountMax();
            return amount.compareTo(min) >= 0 && (max == null || amount.compareTo(max) <= 0);
        }
        return false;
    }

    private void validateSaveRequest(SalarySettlementPolicyDtos.SavePolicyRequest request) {
        if (request == null) {
            throw new BusinessException("请求体不能为空");
        }
        if (!StringUtils.hasText(request.getPolicyName())) {
            throw new BusinessException("规则名称不能为空");
        }
        String subjectType = normalize(request.getSubjectType());
        if (!Set.of(SUBJECT_INTERNAL_STAFF, SUBJECT_DISTRIBUTOR).contains(subjectType)) {
            throw new BusinessException("结算对象仅支持内部员工或外部分销");
        }
        String scopeType = normalize(request.getScopeType());
        if (!Set.of(SCOPE_ROLE, SCOPE_AMOUNT).contains(scopeType)) {
            throw new BusinessException("匹配方式仅支持按角色或按金额");
        }
        if (SCOPE_ROLE.equals(scopeType) && normalizeRoleCodes(request.getRoleCodes()).isEmpty()) {
            throw new BusinessException("按角色匹配时至少选择一个角色");
        }
        if (SCOPE_AMOUNT.equals(scopeType) && request.getAmountMax() != null
                && normalizeAmount(request.getAmountMax()).compareTo(normalizeAmount(request.getAmountMin())) < 0) {
            throw new BusinessException("最高金额不能小于最低金额");
        }
        String settlementCycle = normalize(request.getSettlementCycle());
        if (!Set.of(CYCLE_MONTHLY, CYCLE_INSTANT).contains(settlementCycle)) {
            throw new BusinessException("结算周期仅支持按月或即时");
        }
        String settlementMode = normalize(request.getSettlementMode());
        if (!Set.of(MODE_LEDGER_ONLY, MODE_WITHDRAW_DIRECT, MODE_WITHDRAW_AUDIT).contains(settlementMode)) {
            throw new BusinessException("结算方式不支持");
        }
    }

    private void validatePublishConflicts(SalarySettlementPolicy candidate) {
        List<SalarySettlementPolicy> published = policyMapper.selectList(new LambdaQueryWrapper<SalarySettlementPolicy>()
                .eq(SalarySettlementPolicy::getStatus, STATUS_PUBLISHED)
                .eq(SalarySettlementPolicy::getEnabled, 1)
                .eq(SalarySettlementPolicy::getSubjectType, candidate.getSubjectType()));
        for (SalarySettlementPolicy existing : published) {
            if (Objects.equals(existing.getId(), candidate.getId())
                    || Objects.equals(existing.getId(), candidate.getSourcePolicyId())) {
                continue;
            }
            if (hasConflict(candidate, existing)) {
                throw new BusinessException("规则与已发布规则存在重叠：" + existing.getPolicyName());
            }
        }
    }

    private boolean hasConflict(SalarySettlementPolicy candidate, SalarySettlementPolicy existing) {
        String candidateScope = normalize(candidate.getScopeType());
        String existingScope = normalize(existing.getScopeType());
        if (!candidateScope.equals(existingScope)) {
            return false;
        }
        if (SCOPE_ROLE.equals(candidateScope)) {
            Set<String> candidateRoles = roleCodes(candidate.getRoleCodes());
            return roleCodes(existing.getRoleCodes()).stream().anyMatch(candidateRoles::contains);
        }
        if (SCOPE_AMOUNT.equals(candidateScope)) {
            BigDecimal candidateMin = candidate.getAmountMin() == null ? BigDecimal.ZERO : candidate.getAmountMin();
            BigDecimal candidateMax = candidate.getAmountMax();
            BigDecimal existingMin = existing.getAmountMin() == null ? BigDecimal.ZERO : existing.getAmountMin();
            BigDecimal existingMax = existing.getAmountMax();
            BigDecimal leftMax = candidateMax == null ? existingMax : existingMax == null ? candidateMax : candidateMax.min(existingMax);
            BigDecimal rightMin = candidateMin.max(existingMin);
            return leftMax == null || rightMin.compareTo(leftMax) <= 0;
        }
        return false;
    }

    private void applyRequest(SalarySettlementPolicy policy, SalarySettlementPolicyDtos.SavePolicyRequest request) {
        String scopeType = normalize(request.getScopeType());
        policy.setPolicyName(request.getPolicyName().trim());
        policy.setSubjectType(normalize(request.getSubjectType()));
        policy.setScopeType(scopeType);
        policy.setRoleCodes(SCOPE_ROLE.equals(scopeType) ? String.join(",", normalizeRoleCodes(request.getRoleCodes())) : "");
        policy.setAmountMin(SCOPE_AMOUNT.equals(scopeType) ? normalizeAmount(request.getAmountMin()) : null);
        policy.setAmountMax(SCOPE_AMOUNT.equals(scopeType) ? request.getAmountMax() : null);
        policy.setSettlementCycle(normalize(request.getSettlementCycle()));
        policy.setSettlementMode(normalize(request.getSettlementMode()));
        policy.setAuditThresholdAmount(request.getAuditThresholdAmount());
        policy.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        policy.setRemark(request.getRemark());
    }

    private SalarySettlementPolicy getPolicy(Long policyId) {
        if (policyId == null || policyId <= 0) {
            throw new BusinessException("policyId 不能为空");
        }
        SalarySettlementPolicy policy = policyMapper.selectById(policyId);
        if (policy == null) {
            throw new BusinessException("结算规则不存在");
        }
        return policy;
    }

    private SalarySettlementPolicyDtos.PolicyResponse toResponse(SalarySettlementPolicy policy) {
        SalarySettlementPolicyDtos.PolicyResponse response = new SalarySettlementPolicyDtos.PolicyResponse();
        response.setId(policy.getId());
        response.setSourcePolicyId(policy.getSourcePolicyId());
        response.setPolicyName(policy.getPolicyName());
        response.setSubjectType(policy.getSubjectType());
        response.setScopeType(policy.getScopeType());
        response.setRoleCodes(new ArrayList<>(roleCodes(policy.getRoleCodes())));
        response.setAmountMin(policy.getAmountMin());
        response.setAmountMax(policy.getAmountMax());
        response.setSettlementCycle(policy.getSettlementCycle());
        response.setSettlementMode(policy.getSettlementMode());
        response.setAuditThresholdAmount(policy.getAuditThresholdAmount());
        response.setPriority(policy.getPriority());
        response.setEnabled(policy.getEnabled());
        response.setStatus(policy.getStatus());
        response.setRemark(policy.getRemark());
        response.setCreateTime(policy.getCreateTime());
        response.setUpdateTime(policy.getUpdateTime());
        response.setPublishedTime(policy.getPublishedTime());
        return response;
    }

    private String resolveNextAction(SalarySettlementPolicy policy) {
        String mode = normalize(policy.getSettlementMode());
        if (MODE_LEDGER_ONLY.equals(mode)) {
            return "按月生成内部员工结算台账，不发起真实资金处理";
        }
        if (MODE_WITHDRAW_DIRECT.equals(mode)) {
            return "登记外部已处理结果，不在本系统发起打款";
        }
        if (MODE_WITHDRAW_AUDIT.equals(mode)) {
            return "创建线下处理登记，进入财务审核确认";
        }
        return "按规则继续处理";
    }

    private void audit(SalarySettlementPolicy policy,
                       String actionType,
                       PermissionRequestContext context,
                       String summary) {
        if (policy == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO salary_settlement_policy_audit_log(
                    policy_id, action_type, actor_role_code, actor_user_id, summary, snapshot_json, create_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, policy.getId(), actionType,
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                summary,
                snapshot(policy),
                LocalDateTime.now());
    }

    private String snapshot(SalarySettlementPolicy policy) {
        return "{"
                + "\"id\":" + policy.getId()
                + ",\"sourcePolicyId\":" + policy.getSourcePolicyId()
                + ",\"policyName\":\"" + escape(policy.getPolicyName()) + "\""
                + ",\"subjectType\":\"" + escape(policy.getSubjectType()) + "\""
                + ",\"scopeType\":\"" + escape(policy.getScopeType()) + "\""
                + ",\"roleCodes\":\"" + escape(policy.getRoleCodes()) + "\""
                + ",\"amountMin\":\"" + (policy.getAmountMin() == null ? "" : policy.getAmountMin()) + "\""
                + ",\"amountMax\":\"" + (policy.getAmountMax() == null ? "" : policy.getAmountMax()) + "\""
                + ",\"settlementCycle\":\"" + escape(policy.getSettlementCycle()) + "\""
                + ",\"settlementMode\":\"" + escape(policy.getSettlementMode()) + "\""
                + ",\"priority\":" + policy.getPriority()
                + ",\"enabled\":" + policy.getEnabled()
                + ",\"status\":\"" + escape(policy.getStatus()) + "\""
                + "}";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
    }

    private List<String> normalizeRoleCodes(List<String> roleCodes) {
        if (roleCodes == null) {
            return List.of();
        }
        return roleCodes.stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Set<String> roleCodes(String roleCodes) {
        if (!StringUtils.hasText(roleCodes)) {
            return Set.of();
        }
        return Arrays.stream(roleCodes.split(","))
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .sorted(Comparator.naturalOrder())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized : defaultValue;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
