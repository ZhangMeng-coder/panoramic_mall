-- ============================================================
-- 迁移：店铺在售商品锁定能力（store_goods_spu 加锁定列）
-- 日期：2026-09-12   范围：store_goods_spu（store 域）
-- 背景：管理后台需要「店铺商品管理」——平台可锁定/解锁店铺商品；
--       锁定后商品级联下架（名下 SKU 全部下架，SPU 由 refreshShelfStatus 推导为下架），
--       锁定期店铺端整行只读，仅平台可解锁；解锁不自动恢复上架（由店主手动上架）。
--       因此需要 4 个业务列记录锁定时点与原因，便于店主端展示「锁定信息」。
-- 说明：lock_user 是业务列（不是审计列），存 UserType:UserId 原串（如 admin:1），
--       由域内从 UserContext 直取，不做平台用户表联查（A2/D13）。
-- 幂等：四列与索引各自先查 information_schema 再决定是否 ALTER，可重复执行。
--       （等价于 backend/store/src/main/resources/db/schema.sql 中对存量库的补列块；
--        schema.sql 是「新建/修复」入口，本文件是「已部署库」的一次性迁移留痕。）
-- 执行方式：node <mysql-connect>/scripts/query.mjs --write --file <本文件>
--           （本机无 mysql CLI，走个人 skill mysql-connect；MySQL 8 无 ADD COLUMN IF NOT EXISTS，
--             故用 PREPARE 动态 DDL 做幂等，重复执行安全。）
-- 回滚（如需）：
--   ALTER TABLE store_goods_spu
--     DROP INDEX idx_lock_status,
--     DROP COLUMN lock_status, DROP COLUMN lock_reason,
--     DROP COLUMN lock_user,   DROP COLUMN lock_time;
-- ============================================================

SET @has_lock_status := (SELECT COUNT(*) FROM information_schema.COLUMNS
                         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_goods_spu' AND COLUMN_NAME = 'lock_status');
SET @ddl := IF(@has_lock_status = 0,
  'ALTER TABLE store_goods_spu ADD COLUMN lock_status TINYINT NOT NULL DEFAULT 0 COMMENT ''锁定状态：0未锁定，1已锁定'' AFTER shelf_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_lock_reason := (SELECT COUNT(*) FROM information_schema.COLUMNS
                         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_goods_spu' AND COLUMN_NAME = 'lock_reason');
SET @ddl := IF(@has_lock_reason = 0,
  'ALTER TABLE store_goods_spu ADD COLUMN lock_reason VARCHAR(255) DEFAULT NULL COMMENT ''锁定原因（锁定时必填）'' AFTER lock_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_lock_user := (SELECT COUNT(*) FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_goods_spu' AND COLUMN_NAME = 'lock_user');
SET @ddl := IF(@has_lock_user = 0,
  'ALTER TABLE store_goods_spu ADD COLUMN lock_user VARCHAR(32) DEFAULT NULL COMMENT ''锁定人（UserType:UserId，如 admin:1）'' AFTER lock_reason',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_lock_time := (SELECT COUNT(*) FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_goods_spu' AND COLUMN_NAME = 'lock_time');
SET @ddl := IF(@has_lock_time = 0,
  'ALTER TABLE store_goods_spu ADD COLUMN lock_time DATETIME DEFAULT NULL COMMENT ''锁定时间'' AFTER lock_user',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_lock_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_goods_spu' AND INDEX_NAME = 'idx_lock_status');
SET @ddl := IF(@has_lock_idx = 0,
  'ALTER TABLE store_goods_spu ADD INDEX idx_lock_status (lock_status)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
