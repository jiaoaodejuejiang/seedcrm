package com.seedcrm.crm.role.controller;

import com.seedcrm.crm.role.entity.RoleConfig;
import com.seedcrm.crm.role.service.RoleConfigService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/role")
public class RoleConfigController {

    private final RoleConfigService roleConfigService;

    public RoleConfigController(RoleConfigService roleConfigService) {
        this.roleConfigService = roleConfigService;
    }

    @GetMapping("/list")
    public List<RoleConfig> list() {
        return roleConfigService.list();
    }

    @PostMapping("/add")
    public RoleConfig add(@RequestBody RoleConfig roleConfig) {
        if (roleConfig == null
                || !StringUtils.hasText(roleConfig.getRoleCode())
                || !StringUtils.hasText(roleConfig.getRoleName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roleCode and roleName are required");
        }
        roleConfigService.save(roleConfig);
        return roleConfig;
    }

    @GetMapping("/test")
    public String test() {
        return "role module ok";
    }
}
