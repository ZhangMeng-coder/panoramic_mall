-- ============================================================
-- 全景商城 store 业务域（下沉纯域）建表脚本（MySQL 8）
-- 说明：本切片 store 域仅持 store_shop 一张表；店主账号表 store_user
--       已随 BFF 化下沉到 store-bff（店主端 BFF），见 store-bff 模块 schema。
--       列名与 common BaseEntity 字段对应（create_user/update_user/is_delete）。
--       可重复执行（CREATE TABLE IF NOT EXISTS）。
-- 执行方式：mysql -uroot -p < schema.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS panoramic_mall
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE panoramic_mall;

-- 店铺表（账号店同 ID：一人一店，主键 = 店主账号 id）
--   「账号店同 ID」决策：店铺主键 id == 店主账号 id（store_user.id），
--   建店时由 store-bff 以店主账号 id 显式插入（不依赖 DB 自增）；
--   因店铺主键即归属，不再需要 owner_user_id 列与 uk_owner_user_id 唯一键。
--   审核状态机：0草稿 / 1待审核 / 2已通过 / 3已驳回；审核字段仅记录留痕，不与平台用户表联查。
CREATE TABLE IF NOT EXISTS store_shop (
  id            BIGINT UNSIGNED NOT NULL            COMMENT '主键（=店主账号 id，一人一店，IdType.INPUT 显式插入）',
  shop_name     VARCHAR(50)     DEFAULT NULL        COMMENT '店铺名称',
  logo          VARCHAR(255)    DEFAULT NULL        COMMENT '店铺 LOGO 图片 URL',
  intro         VARCHAR(500)    DEFAULT NULL        COMMENT '店铺简介',
  contact_name  VARCHAR(50)     DEFAULT NULL        COMMENT '联系人姓名',
  contact_phone VARCHAR(20)     DEFAULT NULL        COMMENT '联系人电话',
  region        VARCHAR(100)    DEFAULT NULL        COMMENT '所在地区（省市区）',
  address       VARCHAR(255)    DEFAULT NULL        COMMENT '详细地址',
  license_name  VARCHAR(100)    DEFAULT NULL        COMMENT '营业执照企业名称',
  license_no    VARCHAR(50)     DEFAULT NULL        COMMENT '统一社会信用代码',
  license_img   VARCHAR(255)    DEFAULT NULL        COMMENT '营业执照照片 URL',
  status        TINYINT         NOT NULL DEFAULT 0  COMMENT '审核状态：0草稿，1待审核，2已通过，3已驳回',
  submit_time   DATETIME        DEFAULT NULL        COMMENT '最近一次提交审核时间',
  audit_by      BIGINT UNSIGNED DEFAULT NULL        COMMENT '审核人ID（平台管理员，仅记录）',
  audit_time    DATETIME        DEFAULT NULL        COMMENT '审核时间',
  audit_remark  VARCHAR(255)    DEFAULT NULL        COMMENT '审核备注（驳回原因）',
  create_user   INT             DEFAULT NULL        COMMENT '创建人ID',
  create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user   INT             DEFAULT NULL        COMMENT '更新人ID',
  update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete     TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_status (status),
  KEY idx_submit_time (submit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺表';
