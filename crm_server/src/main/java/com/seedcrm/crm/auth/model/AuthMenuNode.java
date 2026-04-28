package com.seedcrm.crm.auth.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthMenuNode {

    private String key;
    private String label;
    private String routePath;
    private String moduleCode;
    private String permissionCode;
    private List<AuthMenuNode> children = new ArrayList<>();

    public AuthMenuNode(String key, String label, String routePath, String moduleCode, String permissionCode) {
        this.key = key;
        this.label = label;
        this.routePath = routePath;
        this.moduleCode = moduleCode;
        this.permissionCode = permissionCode;
    }
}
