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
--   lock_*：平台锁定（管理后台「店铺商品管理」）。锁定 → 名下 SKU 全部下架、SPU 随之推导为下架；
--   锁定期 owner 侧整行只读（编辑/上下架/增删改 SKU/删除 全部拒绝）；仅平台可解锁，解锁不自动恢复上架。
--   lock_user 是业务列（非审计列），存 UserType:UserId 原串（如 admin:1），仅管理端展示，店铺端不展示。
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
  lock_status    TINYINT         NOT NULL DEFAULT 0      COMMENT '锁定状态：0未锁定，1已锁定（平台锁定）',
  lock_reason    VARCHAR(255)    DEFAULT NULL            COMMENT '锁定原因（锁定时必填）',
  lock_user      VARCHAR(32)     DEFAULT NULL            COMMENT '锁定人（UserType:UserId，如 admin:1）',
  lock_time      DATETIME        DEFAULT NULL            COMMENT '锁定时间',
  min_price      DECIMAL(10,2)   DEFAULT NULL            COMMENT '在售SKU最低价（推导量，由SKU联动维护）',
  create_user    VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user    VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete      TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_store_id (store_id),
  KEY idx_goods_spu_id (goods_spu_id),
  KEY idx_shelf_status (shelf_status),
  KEY idx_lock_status (lock_status)
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

-- 3.1 幂等加列：为已存在的 store_goods_spu 表补充平台锁定列（可重复执行）。
--     新建库走上面的 CREATE TABLE 即已含这四列，本块只对「早于锁定功能建表」的存量库生效；
--     守卫判定为已存在时跳过，不重复 ALTER。
SET @spu_has_lock := (SELECT COUNT(*) FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_goods_spu' AND COLUMN_NAME = 'lock_status');
SET @spu_lock_ddl := IF(@spu_has_lock = 0,
  'ALTER TABLE store_goods_spu
     ADD COLUMN lock_status TINYINT NOT NULL DEFAULT 0 COMMENT ''锁定状态：0未锁定，1已锁定（平台锁定）'' AFTER shelf_status,
     ADD COLUMN lock_reason VARCHAR(255) DEFAULT NULL COMMENT ''锁定原因（锁定时必填）'' AFTER lock_status,
     ADD COLUMN lock_user VARCHAR(32) DEFAULT NULL COMMENT ''锁定人（UserType:UserId，如 admin:1）'' AFTER lock_reason,
     ADD COLUMN lock_time DATETIME DEFAULT NULL COMMENT ''锁定时间'' AFTER lock_user,
     ADD INDEX idx_lock_status (lock_status)',
  'SELECT 1');
PREPARE spu_lock_stmt FROM @spu_lock_ddl; EXECUTE spu_lock_stmt; DEALLOCATE PREPARE spu_lock_stmt;

-- 3.2 幂等加列：为已存在的 store_goods_spu 表补充在售最低价列（可重复执行）。
--     新建库走上面的 CREATE TABLE 即已含该列，本块只对「早于本功能建表」的存量库生效；
--     守卫判定为已存在时跳过，不重复 ALTER。
SET @spu_has_min_price := (SELECT COUNT(*) FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_goods_spu' AND COLUMN_NAME = 'min_price');
SET @spu_min_price_ddl := IF(@spu_has_min_price = 0,
  'ALTER TABLE store_goods_spu
     ADD COLUMN min_price DECIMAL(10,2) DEFAULT NULL COMMENT ''在售SKU最低价（推导量，由SKU联动维护）'' AFTER lock_time',
  'SELECT 1');
PREPARE spu_min_price_stmt FROM @spu_min_price_ddl; EXECUTE spu_min_price_stmt; DEALLOCATE PREPARE spu_min_price_stmt;

-- 3.3 存量回填：按不变量「min_price = 上架且未删 SKU 的最低价」重算全部存量行（可重复执行）
UPDATE store_goods_spu s
   SET s.min_price = (SELECT MIN(k.price) FROM store_goods_sku k
                       WHERE k.spu_id = s.id AND k.is_delete = 0 AND k.shelf_status = 1)
 WHERE s.is_delete = 0;

-- 4. SKU 库存表（与 store_goods_sku 1:1，**独立成表**）
--   独立成表的理由：库存若加成 store_goods_sku 的一列，改库存的 UPDATE 会锁住该 SKU 行，
--   而商品编辑 / 上下架 / 平台锁定 / refreshDerived 都在改同一行 —— 补货会与商品运维互相阻塞。
--   独立后库存行的写锁只覆盖库存行本身（R14）。
--   不冗余 store_id / spu_id：归属链 sku_id → store_goods_sku.spu_id → store_goods_spu.store_id。
--   available(可用库存) = stock − locked_stock；C 端展示的一律是它（本期 locked_stock 恒 0）。
--   warn_stock 仅商户端低库存预警用，NULL = 不预警，**不进 C 端**。
--   库存不参与「SPU上架 ⟺ ≥1 SKU 上架」不变量，也不参与 C 端可见性（零库存不触发下架、商品照常可见）。
CREATE TABLE IF NOT EXISTS store_goods_sku_stock (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  sku_id       BIGINT UNSIGNED NOT NULL                COMMENT 'store_goods_sku.id（一 SKU 一行）',
  stock        INT             NOT NULL DEFAULT 0      COMMENT '总库存（商户维护）',
  locked_stock INT             NOT NULL DEFAULT 0      COMMENT '占用库存（交易域写入，本期恒 0）',
  warn_stock   INT             DEFAULT NULL            COMMENT '低库存预警阈值（NULL = 不预警，仅商户端用）',
  create_user  VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user  VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sku_id (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺在售商品 SKU 库存（与 store_goods_sku 1:1）';

-- 4.1 存量回填（幂等，纯 INSERT、不改任何存量行）：未删 SKU 一律补 0 行
INSERT INTO store_goods_sku_stock (sku_id, stock, locked_stock, warn_stock, is_delete)
SELECT s.id, 0, 0, NULL, 0 FROM store_goods_sku s
WHERE s.is_delete = 0
  AND NOT EXISTS (SELECT 1 FROM store_goods_sku_stock t WHERE t.sku_id = s.id);
