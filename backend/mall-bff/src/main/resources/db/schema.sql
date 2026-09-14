-- ============================================================
-- 全景商城 商城前台 BFF（mall-bff）建表脚本（MySQL 8）
-- 说明：本模块只持 mall_user（C 端顾客账号栈）一张表；商品/分类归 goods-center，
--       购物车/订单归未来的 trade-center。列名与 common BaseEntity 字段对应
--       （create_user/update_user/is_delete）。可重复执行（CREATE TABLE IF NOT EXISTS）。
--       账号表与其他端同库 panoramic_mall，仅划分表所有权、不复刻。
-- 执行方式：mysql -uroot -p < schema.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS panoramic_mall
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE panoramic_mall;

-- 1. C 端顾客账号表（独立账号体系，与平台管理员 sys_user、店主 store_user 分开；
--    id 空间由身份维度 userType=user 经 Redis 键与 JWT type claim 隔离）
--    账号即手机号：phone 为登录账号，唯一；password 为占位列——本期不读写，
--    保留列是为了与 store_user 形状一致（将来加密码登录/改密时不需迁移表）。
CREATE TABLE IF NOT EXISTS mall_user (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  phone       VARCHAR(20)     NOT NULL              COMMENT '手机号（登录账号）',
  password    VARCHAR(100)    DEFAULT NULL          COMMENT '密码（BCrypt 加密；占位，本期不读写）',
  nickname    VARCHAR(50)     DEFAULT NULL          COMMENT '昵称',
  status      TINYINT         NOT NULL DEFAULT 1    COMMENT '状态：1 启用，0 停用',
  create_user VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone (phone),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='C 端顾客账号表';
