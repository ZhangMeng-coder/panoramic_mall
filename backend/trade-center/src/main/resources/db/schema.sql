-- ============================================================
-- 全景商城 trade-center 交易域（下沉纯域）建表脚本（MySQL 8）
-- 说明：trade-center 本期**只持购物车** trade_cart_item（订单 / 结账 / 评价不在本期，见仓库根 todo.md）；
--       顾客账号表 mall_user 在 mall-bff、店铺商品 store_goods_spu/sku 在 store 域，本表只记 id 引用（无外键）。
--       列名与 common BaseEntity 字段对应（create_user/update_user/is_delete）。
--       可重复执行（CREATE TABLE IF NOT EXISTS），纯新增、不改任何存量行。
-- 执行方式：mysql -uroot -p < schema.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS panoramic_mall
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE panoramic_mall;

-- 购物车行（一行 = 一个 SKU；同一顾客同一 SKU 至多一行，重复加购是累加数量）
CREATE TABLE IF NOT EXISTS trade_cart_item (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  customer_id BIGINT UNSIGNED NOT NULL COMMENT '所属顾客（=mall_user.id）',
  spu_id      BIGINT UNSIGNED NOT NULL COMMENT '店铺商品 SPU id（store 域）',
  sku_id      BIGINT UNSIGNED NOT NULL COMMENT '店铺商品 SKU id（store 域）',
  quantity    INT             NOT NULL DEFAULT 1 COMMENT '数量：1..999',
  selected    TINYINT         NOT NULL DEFAULT 1 COMMENT '选中状态：0未选，1选中',
  create_user VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除位——⚠本表恒 0：购物车走物理删除（唯一键 (customer_id,sku_id) 与逻辑删除互斥），该列只为对齐 BaseEntity 而保留',
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_sku (customer_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车行';
