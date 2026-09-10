-- ============================================================
-- 迁移：审计字段 Integer → VARCHAR(32)，值改为 UserType:UserId
-- 日期：2026-09-10   范围：store_user（店主账号，归店铺域，表建在 store-bff 模块 db 目录）
-- 背景：多端身份空间（admin/store/user）下同一 id 可能指向不同的人，
--       审计字段需带类型前缀消歧（如 admin:1 / store:7）。
-- 可重复执行：ALTER 本身幂等；回填带 NOT LIKE '%:%' 守卫，重跑不会二次加前缀。
-- 回填前缀：store_user 归店铺域 → store:（已确认：按所属端统一回填，历史留痕不精确可接受）。
-- ============================================================

ALTER TABLE store_user
  MODIFY COLUMN create_user VARCHAR(32) DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  MODIFY COLUMN update_user VARCHAR(32) DEFAULT NULL COMMENT '更新人（UserType:UserId）';
UPDATE store_user SET create_user = CONCAT('store:', create_user)
  WHERE create_user IS NOT NULL AND create_user NOT LIKE '%:%';
UPDATE store_user SET update_user = CONCAT('store:', update_user)
  WHERE update_user IS NOT NULL AND update_user NOT LIKE '%:%';
