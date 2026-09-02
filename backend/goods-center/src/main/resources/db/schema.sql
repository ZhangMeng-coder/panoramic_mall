-- ============================================================
-- 全景商城 商品中台 建库建表脚本（MySQL 8）
-- 说明：可重复执行（CREATE TABLE IF NOT EXISTS）；列名与 common
--       BaseEntity 字段对应（create_user/update_user/is_delete）。
-- 执行方式：mysql -uroot -p < schema.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS panoramic_mall
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE panoramic_mall;

-- 1. 商品分类表（多级树）
CREATE TABLE IF NOT EXISTS goods_category (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  parent_id   BIGINT UNSIGNED NOT NULL DEFAULT 0    COMMENT '父分类ID，0 表示顶级',
  name        VARCHAR(64)     NOT NULL              COMMENT '分类名称',
  level       TINYINT         NOT NULL DEFAULT 1    COMMENT '层级：1=顶级',
  sort        INT             NOT NULL DEFAULT 0    COMMENT '排序值，越小越靠前',
  create_user INT             DEFAULT NULL          COMMENT '创建人ID',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user INT             DEFAULT NULL          COMMENT '更新人ID',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表（多级树）';

-- 2. 商品品牌表
CREATE TABLE IF NOT EXISTS goods_brand (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(64)     NOT NULL              COMMENT '品牌名称',
  logo        VARCHAR(255)    DEFAULT NULL          COMMENT '品牌 LOGO URL',
  description VARCHAR(500)    DEFAULT NULL          COMMENT '品牌简介',
  sort        INT             NOT NULL DEFAULT 0    COMMENT '排序值，越小越靠前',
  create_user INT             DEFAULT NULL          COMMENT '创建人ID',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user INT             DEFAULT NULL          COMMENT '更新人ID',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品品牌表';

-- 3. 商品SPU表
CREATE TABLE IF NOT EXISTS goods_spu (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(128)    NOT NULL              COMMENT '商品名称',
  category_id BIGINT UNSIGNED NOT NULL              COMMENT '所属分类ID（要求叶子分类）',
  brand_id    BIGINT UNSIGNED NOT NULL              COMMENT '品牌ID',
  main_image  VARCHAR(255)    DEFAULT NULL          COMMENT '主图 URL',
  image_list  JSON            DEFAULT NULL          COMMENT '轮播图 URL 数组，如 ["url1","url2"]',
  description TEXT            DEFAULT NULL          COMMENT '商品详情（富文本 HTML）',
  status      TINYINT         NOT NULL DEFAULT 0    COMMENT '状态：0 下架，1 上架',
  create_user INT             DEFAULT NULL          COMMENT '创建人ID',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user INT             DEFAULT NULL          COMMENT '更新人ID',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_category_id (category_id),
  KEY idx_brand_id (brand_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SPU表';

-- 4. 商品SKU表（规格组合，无价格库存）
CREATE TABLE IF NOT EXISTS goods_sku (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  spu_id      BIGINT UNSIGNED NOT NULL              COMMENT '所属 SPU ID',
  spec_attrs  JSON            NOT NULL              COMMENT '规格属性组合：[{"spec":"颜色","value":"黑色"},{"spec":"内存","value":"256G"}]',
  sku_code    VARCHAR(64)     DEFAULT NULL          COMMENT '商家自定义 SKU 编码（可选）',
  main_image  VARCHAR(255)    DEFAULT NULL          COMMENT 'SKU 图片 URL（可选）',
  create_user INT             DEFAULT NULL          COMMENT '创建人ID',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user INT             DEFAULT NULL          COMMENT '更新人ID',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_spu_id (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表（规格属性组合，无价格库存）';
