package com.herbaltea.module.auth.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点（v10 授权页勾选树）
 *
 * <p>三级结构：菜单（type=1）→ 按钮（type=2）→ 接口（type=3），
 * isSensitive=1 节点仅超管角色可授予（前端可置灰提示）。
 */
public record PermissionNodeVO(
        Long id,
        String code,
        String name,
        String module,
        Integer type,
        Long parentId,
        String path,
        Integer isSensitive,
        List<PermissionNodeVO> children
) {
    public PermissionNodeVO(Long id, String code, String name, String module,
                            Integer type, Long parentId, String path, Integer isSensitive) {
        this(id, code, name, module, type, parentId, path, isSensitive, new ArrayList<>());
    }
}
