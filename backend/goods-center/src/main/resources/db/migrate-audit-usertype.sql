-- ============================================================
-- 迁移：审计字段 Integer → VARCHAR(32)，值改为 UserType:UserId
-- 日期：2026-09-10   范围：goods_category / goods_brand / goods_spu / goods_sku
-- 背景：多端身份空间（admin/store/user）下同一 id 可能指向不同的人，
--       审计字段需带类型前缀消歧（如 admin:1 / store:7）。
-- 可重复执行：ALTER 本身幂等；回填带 NOT LIKE '%:%' 守卫，重跑不会二次加前缀。
-- 回填前缀：goods_* 属标准商品模板库，写入方为平台管理员 → admin:。
-- ============================================================

ALTER TABLE goods_category
  MODIFY COLUMN create_user VARCHAR(32) DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  MODIFY COLUMN update_user VARCHAR(32) DEFAULT NULL COMMENT '更新人（UserType:UserId）';
UPDATE goods_category SET create_user = CONCAT('admin:', create_user)
  WHERE create_user IS NOT NULL AND create_user NOT LIKE '%:%';
UPDATE goods_category SET update_user = CONCAT('admin:', update_user)
  WHERE update_user IS NOT NULL AND update_user NOT LIKE '%:%';

ALTER TABLE goods_brand
  MODIFY COLUMN create_user VARCHAR(32) DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  MODIFY COLUMN update_user VARCHAR(32) DEFAULT NULL COMMENT '更新人（UserType:UserId）';
UPDATE goods_brand SET create_user = CONCAT('admin:', create_user)
  WHERE create_user IS NOT NULL AND create_user NOT LIKE '%:%';
UPDATE goods_brand SET update_user = CONCAT('admin:', update_user)
  WHERE update_user IS NOT NULL AND update_user NOT LIKE '%:%';

ALTER TABLE goods_spu
  MODIFY COLUMN create_user VARCHAR(32) DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  MODIFY COLUMN update_user VARCHAR(32) DEFAULT NULL COMMENT '更新人（UserType:UserId）';
UPDATE goods_spu SET create_user = CONCAT('admin:', create_user)
  WHERE create_user IS NOT NULL AND create_user NOT LIKE '%:%';
UPDATE goods_spu SET update_user = CONCAT('admin:', update_user)
  WHERE update_user IS NOT NULL AND update_user NOT LIKE '%:%';

ALTER TABLE goods_sku
  MODIFY COLUMN create_user VARCHAR(32) DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  MODIFY COLUMN update_user VARCHAR(32) DEFAULT NULL COMMENT '更新人（UserType:UserId）';
UPDATE goods_sku SET create_user = CONCAT('admin:', create_user)
  WHERE create_user IS NOT NULL AND create_user NOT LIKE '%:%';
UPDATE goods_sku SET update_user = CONCAT('admin:', update_user)
  WHERE update_user IS NOT NULL AND update_user NOT LIKE '%:%';
