-- ============================================================
-- V17: B 端 Excel 导出任务表（v33）
-- ============================================================
-- 异步导出：避免大表导出阻塞请求；历史任务可下载、可清理。
-- status: 0 待处理 10 处理中 20 成功 30 失败

CREATE TABLE export_tasks (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_no         VARCHAR(64)  NOT NULL,
    biz_type        VARCHAR(32)  NOT NULL COMMENT 'product/order/member/settlement',
    operator_id     BIGINT       NOT NULL COMMENT '触发人 admin_users.id',
    operator_name   VARCHAR(64)  NOT NULL COMMENT '冗余：触发人名',
    filter_json     TEXT         NULL     COMMENT '请求过滤条件 JSON（storeId/status/dateFrom/dateTo 等）',
    row_count       INT          NOT NULL DEFAULT 0,
    file_size       BIGINT       NOT NULL DEFAULT 0 COMMENT '字节',
    file_path       VARCHAR(512) NULL     COMMENT '相对 backend/ 路径，prod 应走 OSS',
    status          TINYINT      NOT NULL DEFAULT 0,
    error_msg       VARCHAR(1024) NULL,
    started_at      DATETIME     NULL,
    finished_at     DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_export_status (status, created_at),
    KEY idx_export_operator (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B 端导出任务表';

-- ============================================================
-- 1) 权限：导出菜单 + 导出操作（敏感仅超管）
-- ============================================================
-- 现有权限回收：112/227 是空位（v23 用了 218-219 给申诉复核，v24-v31 也用了，但 227 已被 v32 通知管理
-- 占了，验证后 id 227 改用 228）
INSERT INTO permissions (id, code, name, module, type, parent_id, path, is_sensitive) VALUES
(112, 'menu:export', '导出中心', 'export', 1, NULL, '/export', 0),
(228, 'export:run', '触发导出', 'export', 2, 112, NULL, 1);

-- ============================================================
-- 2) 默认授权：超管全量；其余角色菜单可见（个人任务页），但 export:run 仅超管可触发
-- ============================================================
-- 所有角色（含财务/店长/店员/仓管）可见 menu:export（看自己的历史）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, 112 FROM roles r WHERE r.code IN ('SUPER_ADMIN','PLATFORM_FINANCE','STORE_ADMIN','STORE_STAFF','WAREHOUSE');

-- export:run 仅超管（敏感）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, 228 FROM roles r WHERE r.code = 'SUPER_ADMIN';
