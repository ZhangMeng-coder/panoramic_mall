-- ============================================================
-- 迁移：审计字段 Integer → VARCHAR(32)，值改为 UserType:UserId
-- 日期：2026-09-10   范围：store_shop
-- 背景：多端身份空间（admin/store/user）下同一 id 可能指向不同的人，
--       审计字段需带类型前缀消歧（如 admin:1 / store:7）。
-- 可重复执行：ALTER 本身幂等；回填带 NOT LIKE '%:%' 守卫，重跑不会二次加前缀。
-- 回填前缀：store_shop 归店铺域 → store:（已确认：按所属端统一回填。
--   已知代价：平台管理员审核产生的历史 update_user 会被标成 store:，留痕不精确，接受）。
-- 注意：本表 audit_by 是审核人留痕列（平台管理员 id），维持 BIGINT UNSIGNED 不变。
-- ============================================================

ALTER TABLE store_shop
  MODIFY COLUMN create_user VARCHAR(32) DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  MODIFY COLUMN update_user VARCHAR(32) DEFAULT NULL COMMENT '更新人（UserType:UserId）';
UPDATE store_shop SET create_user = CONCAT('store:', create_user)
  WHERE create_user IS NOT NULL AND create_user NOT LIKE '%:%';
UPDATE store_shop SET update_user = CONCAT('store:', update_user)
  WHERE update_user IS NOT NULL AND update_user NOT LIKE '%:%';
