-- ============================================================
-- 全景商城 trade-center 交易域（下沉纯域）建表脚本（MySQL 8）
-- 说明：trade-center 持**购物车** trade_cart_item 与**订单**（trade_order 等 5 张，2026-09-21 阶段一新增）；
--       结账 / 评价不在本期。顾客账号表 mall_user 在 mall-bff、
--       店铺商品 store_goods_spu/sku 在 store 域，本表只记 id 引用（无外键）。
--       末段另建 Seata AT 模式的回滚日志 undo_log（2026-09-22 T12）：业务代码不碰它、
--       全域共用同一个库故全仓只需这一份，trade-center 与 store 都是它的读写方。
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

-- ============================================================
-- 订单（trade-center 订单域，2026-09-21 阶段一新增）
-- 说明：域内订单模型已建完，本批补「真实落库」——5 张表；商品 / 库存两个下游本轮仍是内存脚手架（见 todo 阶段一 T4）。
--       两级幂等：trade_order_submission 的 (customer_id, request_id) 唯一键 = L1 请求级（先占键）；
--                 trade_order.fingerprint = L2 批次指纹（窗口内命中即复用既有单）。
--       子表只记 order_no（业务唯一键），不另存 order_id —— 同一身份不写两份。
--       可重复执行（CREATE TABLE IF NOT EXISTS），纯新增、不改任何存量行。
-- ============================================================

-- 订单提交记录：一次下单请求一行；唯一键即 L1 幂等键（先占键，靠唯一键行锁串行化并发重复提交）
CREATE TABLE IF NOT EXISTS trade_order_submission (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  customer_id BIGINT UNSIGNED NOT NULL COMMENT '下单顾客（=mall_user.id）',
  -- ⚠ 允许 NULL：端 BFF 没带请求号时，「本次提交不做请求级去重」——唯一索引里含 NULL 的行不算重复，
  --    故同一顾客可以有很多条 request_id 为 NULL 的提交记录，而它们永远不会互相挡路
  request_id  VARCHAR(64)     DEFAULT NULL COMMENT '端 BFF 提交的请求号（L1 幂等键；NULL = 本次提交不做请求级去重）',
  create_user VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除位——⚠本表恒 0：提交记录不删行，该列只为对齐 BaseEntity 而保留',
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_request (customer_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单提交记录（L1 请求级幂等）';

-- 提交记录 ↔ 订单 关联（一次请求落了哪几笔单）
-- ⚠ 为什么不是 trade_order.submission_id：L2 复用拿回的订单，其「首次创建者」仍是当初那次提交，
--    故「提交记录 ↔ 订单」只能是多对多，必须单独记一行。
CREATE TABLE IF NOT EXISTS trade_order_submission_order (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  submission_id BIGINT UNSIGNED NOT NULL COMMENT 'trade_order_submission.id',
  order_no      VARCHAR(32)     NOT NULL COMMENT 'trade_order.order_no',
  PRIMARY KEY (id),
  UNIQUE KEY uk_submission_order (submission_id, order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交记录↔订单关联（纯关联表：无审计列、物理删除）';

-- 订单主表（一笔订单一店）
CREATE TABLE IF NOT EXISTS trade_order (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no        VARCHAR(32)     NOT NULL COMMENT '18 位业务单号（yyyyMMddHHmmss+4位序列），对外唯一标识',
  customer_id     BIGINT UNSIGNED NOT NULL COMMENT '下单顾客（=mall_user.id）',
  store_id        BIGINT UNSIGNED NOT NULL COMMENT '店铺（=store_shop.id）',
  store_name      VARCHAR(64)     NOT NULL COMMENT '下单时店铺名快照',
  source          VARCHAR(16)     NOT NULL COMMENT '下单来源：DIRECT（详情页直购）/ CART（购物车结算）',
  request_id      VARCHAR(64)     DEFAULT NULL COMMENT '首次创建本单的请求号（L2 复用单保留原值）',
  fingerprint     CHAR(16)        NOT NULL COMMENT '批次指纹 sha256 前 16 位（L2 幂等键，不含金额与时间）',
  status          VARCHAR(24)     NOT NULL COMMENT '订单状态：PENDING_PAYMENT/PAID/SHIPPED/RECEIVED',
  total_quantity  INT             NOT NULL COMMENT '总件数（=Σ明细数量，封存时对账）',
  total_amount    DECIMAL(12,2)   NOT NULL COMMENT '订单总额（=Σ明细小计，封存时对账）',
  receiver_name   VARCHAR(32)     NOT NULL COMMENT '收件人（下单时地址快照）',
  receiver_phone  VARCHAR(20)     NOT NULL COMMENT '收件人电话（下单时地址快照）',
  receiver_region VARCHAR(128)    NOT NULL COMMENT '省市区（下单时地址快照）',
  receiver_detail VARCHAR(255)    NOT NULL COMMENT '详细地址（下单时地址快照）',
  ship_no         VARCHAR(64)     DEFAULT NULL COMMENT '快递单号（发货时录入）',
  create_user     VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user     VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除位——⚠本表恒 0：订单不删行，该列只为对齐 BaseEntity 而保留',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_customer (customer_id, create_time),
  KEY idx_store (store_id, create_time),
  KEY idx_fingerprint (fingerprint, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表（一笔订单一店）';

-- 订单明细（一行 = 一个 SKU；快照字段下单即冻结，此后商品改名换图不影响已有订单）
CREATE TABLE IF NOT EXISTS trade_order_item (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no    VARCHAR(32)     NOT NULL COMMENT 'trade_order.order_no',
  spu_id      BIGINT UNSIGNED NOT NULL COMMENT '店铺商品 SPU id（store 域）',
  sku_id      BIGINT UNSIGNED NOT NULL COMMENT '店铺商品 SKU id（store 域）',
  goods_name  VARCHAR(128)    NOT NULL COMMENT '下单时冻结的商品名',
  main_image  VARCHAR(255)    DEFAULT NULL COMMENT '下单时冻结的主图 URL',
  spec_attrs  JSON            NOT NULL COMMENT '下单时冻结的规格属性（键值对 JSON，无规格写 {}）',
  unit_price  DECIMAL(10,2)   NOT NULL COMMENT '下单时单价（≥0.01）',
  quantity    INT             NOT NULL COMMENT '数量：1..999',
  subtotal    DECIMAL(12,2)   NOT NULL COMMENT '小计（=单价×数量，两位小数四舍五入）',
  create_user VARCHAR(32)     DEFAULT NULL COMMENT '创建人（UserType:UserId）',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除位——⚠本表恒 0：明细不删行，该列只为对齐 BaseEntity 而保留',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_sku (order_no, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- 状态轨迹（一行 = 一次状态变更；⚠ create_time 即「发生时刻」）。
-- ⚠ 不再另存 pay_time / ship_time / receive_time：那与「对应状态那一行的 create_time」是同一事实的第二份。
-- ⚠ seq = 状态下标（status-flow 配置里的位置，从 0 起）：同一秒内多次变更也能定序，轨迹顺序不靠时间戳猜。
CREATE TABLE IF NOT EXISTS trade_order_status_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no    VARCHAR(32)     NOT NULL COMMENT 'trade_order.order_no',
  seq         INT             NOT NULL COMMENT '状态下标（status-flow 配置里的位置，从 0 起）',
  status      VARCHAR(24)     NOT NULL COMMENT '变更后的状态',
  create_user VARCHAR(32)     DEFAULT NULL COMMENT '变更人（UserType:UserId）',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时刻',
  update_user VARCHAR(32)     DEFAULT NULL COMMENT '更新人（UserType:UserId）',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除位——⚠本表恒 0：轨迹不删行，该列只为对齐 BaseEntity 而保留',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_seq (order_no, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态轨迹';

-- ============ Seata AT 模式回滚日志表（T12） ============
-- ⚠ 全域服务**共用同一个库**（panoramic_mall），故全仓只需一份：本表由 Seata 的 RM 自动读写，
--    业务代码**不碰**它。trade-center 与 store 两域都是 RM（store 的库存扣减 / 回补是分支事务）。
-- ⚠ 纯新增，可重复执行。
CREATE TABLE IF NOT EXISTS undo_log (
  branch_id     BIGINT       NOT NULL COMMENT '分支事务 ID',
  xid           VARCHAR(128) NOT NULL COMMENT '全局事务 ID',
  context       VARCHAR(128) NOT NULL COMMENT 'undo_log 序列化方式等上下文',
  rollback_info LONGBLOB     NOT NULL COMMENT '回滚信息（前后镜像）',
  log_status    INT          NOT NULL COMMENT '0=正常 1=防悬挂',
  log_created   DATETIME(6)  NOT NULL COMMENT '创建时间',
  log_modified  DATETIME(6)  NOT NULL COMMENT '修改时间',
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata AT 模式回滚日志';
