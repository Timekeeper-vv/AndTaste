-- Run once against an existing MySQL deployment before enabling the hardened
-- application configuration. Back up the database first.

-- A missing role must never become an administrator by default.
-- Normalize legacy rows before tightening the column constraint; otherwise a
-- single historical NULL would make the whole migration fail halfway through.
UPDATE `user`
   SET `role`='user'
 WHERE `role` IS NULL OR TRIM(`role`)='';

ALTER TABLE `user`
    MODIFY COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'user';

-- Canonical login identity (user.id) and marketplace identity
-- (platform_user.id) intentionally use an explicit mapping instead of
-- assuming matching auto-increment IDs.
CREATE TABLE IF NOT EXISTS user_platform_identity (
    user_id BIGINT NOT NULL PRIMARY KEY COMMENT 'user.id（统一登录主体）',
    platform_user_id BIGINT NOT NULL UNIQUE COMMENT 'platform_user.id（商城主体）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='统一登录用户与文创商城用户映射';

-- No known demo account is deleted automatically: it may own historical test
-- data in an existing database. Inventory and decommission those accounts as
-- an operator-run change, then reset every remaining privileged password.
