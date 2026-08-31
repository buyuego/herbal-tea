-- =====================================================================
-- V3：User 模块对齐（C 端 R9 即时吊销）
-- users 表补 token_version：禁用/注销时 +1，JWT 即时吊销（R9/D12）
-- 与 admin_users.token_version 语义一致（v8 起 B 端已落地，C 端补齐）
-- =====================================================================
ALTER TABLE users
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT '禁用/注销时 +1，JWT 即时吊销（R9）' AFTER status;
