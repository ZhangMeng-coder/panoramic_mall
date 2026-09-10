-- ============================================================
-- 全景商城 store 业务域（下沉纯域）建表脚本（MySQL 8）
-- 说明：store 域持 store_shop（店铺）+ store_goods_spu/store_goods_sku（店铺在售商品）；
--       店主账号表 store_user 已随 BFF 化下沉到 store-bff（店主端 BFF），见 store-bff 模块 schema。
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
  create_user   VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user   VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete     TINYINT         NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_status (status),
  KEY idx_submit_time (submit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺表';

-- 店铺在售商品 SPU 表
--   字段与中台标准商品（goods-center）同构，但归属店铺（store_id = 店主账号 id，账号店同 ID），
--   并带中台关联（goods_spu_id 可空 + center_version 版本戳快照）与上下架状态。
--   分类/品牌以「id 引用 + 名称快照」落库：下拉数据来自中台，保存时不再回查中台。
--   shelf_status 不独立可改：由名下 SKU 联动推导，不变量为「SPU上架 ⟺ ≥1 个 SKU 上架」。
--   center_version：上次关联/同步时中台 SPU 的 version（Unix 毫秒），编辑时比对可判断中台模板是否已改，
--   不一致时由端 BFF 给店主「同步」按钮；店主可选择覆盖或不覆盖，不阻断保存。
CREATE TABLE IF NOT EXISTS store_goods_spu (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  store_id       BIGINT UNSIGNED NOT NULL                COMMENT '所属店铺 id（=店主账号 id，账号店同 ID）',
  goods_spu_id   BIGINT UNSIGNED DEFAULT NULL            COMMENT '中台关联 SPU id（可空，未关联中台为 NULL）',
  center_version BIGINT          DEFAULT NULL            COMMENT '上次关联/同步时中台 SPU 的版本戳',
  name           VARCHAR(128)    NOT NULL                COMMENT '商品名称',
  category_id    BIGINT UNSIGNED NOT NULL                COMMENT '分类 id（引用中台 goods_category）',
  category_name  VARCHAR(64)     DEFAULT NULL            COMMENT '分类名称快照',
  brand_id       BIGINT UNSIGNED DEFAULT NULL            COMMENT '品牌 id（引用中台 goods_brand，非必填）',
  brand_name     VARCHAR(64)     DEFAULT NULL            COMMENT '品牌名称快照',
  main_image     VARCHAR(255)    DEFAULT NULL            COMMENT '主图 URL',
  image_list     JSON            DEFAULT NULL            COMMENT '轮播图 URL 数组',
  description    TEXT            DEFAULT NULL            COMMENT '商品详情（富文本）',
  spec_config    JSON            DEFAULT NULL            COMMENT '规格属性配置',
  shelf_status   TINYINT         NOT NULL DEFAULT 0      COMMENT '上下架：0下架，1上架（由 SKU 联动推导）',
  create_user    VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user    VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete      TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_store_id (store_id),
  KEY idx_goods_spu_id (goods_spu_id),
  KEY idx_shelf_status (shelf_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺在售商品 SPU';

-- 店铺在售商品 SKU 表
--   与中台标准 SKU 同构（规格组合 + 编码 + 图片），额外带价格 price（中台模板无价格、无库存，本域同样无库存列）。
--   不带 store_id：归属经 spu_id → store_goods_spu.store_id 判定，SKU 侧操作先校验 SPU 归属。
--   已上架 SKU 整行锁死（规格/价格/编码/图片不可改、不可删），须先下架。
CREATE TABLE IF NOT EXISTS store_goods_sku (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  spu_id       BIGINT UNSIGNED NOT NULL                COMMENT '所属店铺商品 SPU id',
  spec_attrs   JSON            NOT NULL                COMMENT '规格属性组合',
  sku_code     VARCHAR(64)     DEFAULT NULL            COMMENT 'SKU 编码',
  main_image   VARCHAR(255)    DEFAULT NULL            COMMENT 'SKU 图片 URL',
  price        DECIMAL(10,2)   NOT NULL                COMMENT '价格（必填，≥0.01）',
  shelf_status TINYINT         NOT NULL DEFAULT 0      COMMENT '上下架：0下架，1上架',
  create_user  VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user  VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_spu_id (spu_id),
  KEY idx_sku_code (sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺在售商品 SKU';
