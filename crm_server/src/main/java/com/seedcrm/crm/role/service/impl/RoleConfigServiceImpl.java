package com.seedcrm.crm.role.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.role.entity.RoleConfig;
import com.seedcrm.crm.role.mapper.RoleConfigMapper;
import com.seedcrm.crm.role.service.RoleConfigService;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoleConfigServiceImpl extends ServiceImpl<RoleConfigMapper, RoleConfig> implements RoleConfigService {

    private final RoleConfigMapper roleConfigMapper;

    public RoleConfigServiceImpl(RoleConfigMapper roleConfigMapper) {
        this.roleConfigMapper = roleConfigMapper;
    }

    @Override
    public boolean save(RoleConfig entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getSort() == null) {
            entity.setSort(0);
        }
        if (entity.getIsEnabled() == null) {
            entity.setIsEnabled(1);
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        return roleConfigMapper.insert(entity) > 0;
    }

    @Override
    public List<RoleConfig> list() {
        return roleConfigMapper.selectList(null);
    }

    @Override
    public RoleConfig getById(Serializable id) {
        return roleConfigMapper.selectById(id);
    }
}
