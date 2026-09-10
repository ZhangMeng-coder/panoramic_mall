-- ============================================================
-- 迁移：审计字段 Integer → VARCHAR(32)，值改为 UserType:UserId
-- 日期：2026-09-10   范围：sys_user / sys_role / sys_permission
-- 背景：多端身份空间（admin/store/user）下同一 id 可能指向不同的人，
--       审计字段需带类型前缀消歧（如 admin:1 / store:7）。
-- 可重复执行：ALTER 本身幂等；回填带 NOT LIKE '%:%' 守卫，重跑不会二次加前缀。
-- ⚠ 存量回填前缀按「表所属端」定：sys_* 与 goods_* 归平台管理员 → admin:；
--   store_user / store_shop 归店铺域 → store:（历史 update_user 若为平台管理员，
--   会被标成 store:，留痕不精确，已确认接受）。
-- ============================================================

ALTER TABLE sys_user
  MODIFY COLUMN create_user VARCHAR(32) DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  MODIFY COLUMN update_user VARCHAR(32) DEFAULT NULL COMMENT '更新人（UserType:UserId）';
UPDATE sys_user SET create_user = CONCAT('admin:', create_user)
  WHERE create_user IS NOT NULL AND create_user NOT LIKE '%:%';
UPDATE sys_user SET update_user = CONCAT('admin:', update_user)
  WHERE update_user IS NOT NULL AND update_user NOT LIKE '%:%';

ALTER TABLE sys_role
  MODIFY COLUMN create_user VARCHAR(32) DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  MODIFY COLUMN update_user VARCHAR(32) DEFAULT NULL COMMENT '更新人（UserType:UserId）';
UPDATE sys_role SET create_user = CONCAT('admin:', create_user)
  WHERE create_user IS NOT NULL AND create_user NOT LIKE '%:%';
UPDATE sys_role SET update_user = CONCAT('admin:', update_user)
  WHERE update_user IS NOT NULL AND update_user NOT LIKE '%:%';

ALTER TABLE sys_permission
  MODIFY COLUMN create_user VARCHAR(32) DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  MODIFY COLUMN update_user VARCHAR(32) DEFAULT NULL COMMENT '更新人（UserType:UserId）';
UPDATE sys_permission SET create_user = CONCAT('admin:', create_user)
  WHERE create_user IS NOT NULL AND create_user NOT LIKE '%:%';
UPDATE sys_permission SET update_user = CONCAT('admin:', update_user)
  WHERE update_user IS NOT NULL AND update_user NOT LIKE '%:%';
