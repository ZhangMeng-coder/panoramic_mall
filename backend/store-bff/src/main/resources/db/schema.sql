-- ============================================================
-- 全景商城 店铺端 BFF（store-bff）建表脚本（MySQL 8）
-- 说明：本模块只持 store_user（店主账号栈）一张表；店铺数据 store_shop 归 store 域
--       （见 store 模块 schema.sql）。列名与 common BaseEntity 字段对应
--       （create_user/update_user/is_delete）。可重复执行（CREATE TABLE IF NOT EXISTS）。
--       账号表物理与 store 域同库 panoramic_mall，仅划分表所有权、不复刻。
-- 执行方式：mysql -uroot -p < schema.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS panoramic_mall
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE panoramic_mall;

-- 1. 店主账号表（独立账号体系，与平台管理员 sys_user 分开；id 空间由 userType 维度隔离；
--    「账号店同 ID」：账号 id 即其店铺主键 store_shop.id，一人一店，本表不新增指针列）
CREATE TABLE IF NOT EXISTS store_user (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(50)     NOT NULL              COMMENT '用户名（登录账号）',
  password    VARCHAR(100)    DEFAULT NULL          COMMENT '密码（BCrypt 加密）',
  nickname    VARCHAR(50)     DEFAULT NULL          COMMENT '昵称/姓名',
  phone       VARCHAR(20)     DEFAULT NULL          COMMENT '手机号',
  status      TINYINT         NOT NULL DEFAULT 1    COMMENT '状态：1 启用，0 停用',
  create_user INT             DEFAULT NULL          COMMENT '创建人ID',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user INT             DEFAULT NULL          COMMENT '更新人ID',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店主账号表';
