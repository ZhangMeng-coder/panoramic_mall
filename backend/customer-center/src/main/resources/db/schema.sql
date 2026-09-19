-- ============================================================
-- 全景商城 customer-center 顾客域（下沉纯域）建表脚本（MySQL 8）
-- 说明：customer-center 域持 customer_profile（顾客资料，一对一：主键 = mall_user.id）
--       + customer_address（收货地址，region 与 store_shop.region 同口径：自由文本单列，不建地区表）；
--       顾客账号表 mall_user（手机号/密码等）在 mall-bff，见 mall-bff 模块 schema，**不在本域**。
--       列名与 common BaseEntity 字段对应（create_user/update_user/is_delete）。
--       可重复执行（CREATE TABLE IF NOT EXISTS），纯新增、不改任何存量行。
-- 执行方式：mysql -uroot -p < schema.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS panoramic_mall
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE panoramic_mall;

-- 顾客资料（一对一：主键 = 账号 id，照 store_shop「账号店同 ID」手法，
-- 建行时由 mall-bff/customer-center 显式插入，不依赖 DB 自增）
CREATE TABLE IF NOT EXISTS customer_profile (
  id          BIGINT UNSIGNED NOT NULL COMMENT '主键（=mall_user.id，一对一，IdType.INPUT 显式插入）',
  nickname    VARCHAR(50)     DEFAULT NULL COMMENT '昵称',
  avatar      VARCHAR(255)    DEFAULT NULL COMMENT '头像 URL',
  gender      TINYINT         DEFAULT NULL COMMENT '性别：0未知，1男，2女',
  birthday    DATE            DEFAULT NULL COMMENT '生日',
  create_user VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾客资料表';

-- 收货地址（region 与 store_shop.region 同口径：自由文本单列，不建地区表）
CREATE TABLE IF NOT EXISTS customer_address (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  customer_id    BIGINT UNSIGNED NOT NULL COMMENT '所属顾客（=mall_user.id）',
  receiver_name  VARCHAR(50)     NOT NULL COMMENT '收件人姓名',
  receiver_phone VARCHAR(20)     NOT NULL COMMENT '收件人手机号',
  region         VARCHAR(100)    DEFAULT NULL COMMENT '省市区（自由文本，如「广东省 深圳市 南山区」）',
  detail_address VARCHAR(255)    NOT NULL COMMENT '详细地址',
  is_default     TINYINT         NOT NULL DEFAULT 0 COMMENT '默认地址：0否，1是（同一顾客至多一条为 1：setDefaultAddress 在同事务内先清位再置位、并先对该顾客行加锁串行化；⚠已知窗口：地址簿为空时两笔并发新增会双双为 1，应用层关不上，见 README C3）',
  create_user    VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user    VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_customer_default (customer_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';
