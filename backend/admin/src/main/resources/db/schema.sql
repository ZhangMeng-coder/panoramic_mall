-- ============================================================
-- 全景商城 用户管理（admin）建库建表脚本（MySQL 8）
-- 说明：可重复执行（CREATE TABLE IF NOT EXISTS）；列名与 common
--       BaseEntity 字段对应（create_user/update_user/is_delete）。
--       sys_user_role / sys_role_permission 为纯关联表，物理删除，
--       不带 BaseEntity 逻辑删除字段。
-- 执行方式：mysql -uroot -p < schema.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS panoramic_mall
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE panoramic_mall;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(50)     NOT NULL              COMMENT '用户名（登录账号）',
  password    VARCHAR(100)    DEFAULT NULL          COMMENT '密码（BCrypt 加密）',
  nickname    VARCHAR(50)     DEFAULT NULL          COMMENT '昵称/姓名',
  phone       VARCHAR(20)     DEFAULT NULL          COMMENT '手机号',
  email       VARCHAR(100)    DEFAULT NULL          COMMENT '邮箱',
  avatar      VARCHAR(255)    DEFAULT NULL          COMMENT '头像 URL',
  status      TINYINT         NOT NULL DEFAULT 1    COMMENT '状态：1 启用，0 停用',
  create_user INT             DEFAULT NULL          COMMENT '创建人ID',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user INT             DEFAULT NULL          COMMENT '更新人ID',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_username (username),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 角色表
CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(50)     NOT NULL              COMMENT '角色名称',
  code        VARCHAR(50)     NOT NULL              COMMENT '角色标识（如 admin）',
  description VARCHAR(255)    DEFAULT NULL          COMMENT '角色描述',
  sort        INT             NOT NULL DEFAULT 0    COMMENT '排序值，越小越靠前',
  create_user INT             DEFAULT NULL          COMMENT '创建人ID',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user INT             DEFAULT NULL          COMMENT '更新人ID',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 3. 权限表（多级树：1=目录，2=页面，3=按钮，层级自上而下递减）
CREATE TABLE IF NOT EXISTS sys_permission (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  parent_id   BIGINT UNSIGNED NOT NULL DEFAULT 0    COMMENT '父权限ID，0 表示顶级（仅目录）',
  name        VARCHAR(50)     NOT NULL              COMMENT '权限名称（菜单/按钮名）',
  type        TINYINT         NOT NULL              COMMENT '类型：1=目录，2=页面，3=按钮',
  perms       VARCHAR(128)    DEFAULT NULL          COMMENT '权限字符串，如 system:user:list',
  icon        VARCHAR(128)    DEFAULT NULL          COMMENT '菜单图标',
  route       VARCHAR(200)    DEFAULT NULL          COMMENT '页面路由地址（仅 type=2 页面，如 /user）',
  sort        INT             NOT NULL DEFAULT 0    COMMENT '排序值，越小越靠前',
  create_user INT             DEFAULT NULL          COMMENT '创建人ID',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_user INT             DEFAULT NULL          COMMENT '更新人ID',
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_delete   TINYINT         NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 正常，1 已删除',
  PRIMARY KEY (id),
  KEY idx_parent_id (parent_id),
  KEY idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表（目录/页面/按钮树）';

-- 4. 用户角色关联表（物理删除，无逻辑删除字段）
CREATE TABLE IF NOT EXISTS sys_user_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT UNSIGNED NOT NULL              COMMENT '用户ID',
  role_id     BIGINT UNSIGNED NOT NULL              COMMENT '角色ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 5. 角色权限关联表（物理删除，无逻辑删除字段）
CREATE TABLE IF NOT EXISTS sys_role_permission (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id       BIGINT UNSIGNED NOT NULL              COMMENT '角色ID',
  permission_id BIGINT UNSIGNED NOT NULL              COMMENT '权限ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, permission_id),
  KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ============================================================
-- 兼容已建表：sys_permission 缺 route 列则补齐（重复执行安全）
-- ============================================================
SET @has_route := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_permission' AND COLUMN_NAME = 'route');
SET @ddl := IF(@has_route = 0,
  'ALTER TABLE sys_permission ADD COLUMN route VARCHAR(200) DEFAULT NULL COMMENT ''页面路由地址（仅 type=2 页面，如 /user）'' AFTER icon',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 幂等权限种子：商品中台 / 系统管理 / 店铺管理 目录 → 页面（带路由地址）→ 功能按钮
-- 使用显式 ID + INSERT IGNORE，重复执行不会产生重复数据；
-- 已存在页面（旧种子无 route）由文件尾部 UPDATE 幂等回填路由地址。
-- 注：主页（/home）不再入权限表，由 admin/店铺端 前端各自写死置顶菜单；
--     历史种子中的 主页 目录(id 3)/页面(id 31) 见 db/remove-home-permission.sql。
-- ============================================================
INSERT IGNORE INTO sys_permission (id, parent_id, name, type, perms, icon, sort, route) VALUES
  -- 目录（type=1，parent=0）
  (2,   0,  '商品中台', 1, NULL,             'folder-opened', 0, NULL),
  (1,   0,  '系统管理', 1, NULL,             'setting',       1, NULL),
  -- 页面（type=2，parent=商品中台，供前端动态菜单导航）
  (21,  2,  '分类管理', 2, 'goods:category',   'menu',  1, '/category'),
  (22,  2,  '品牌管理', 2, 'goods:brand',      'goods', 2, '/brand'),
  (23,  2,  '商品管理', 2, 'goods:spu',        'box',   3, '/spu'),
  -- 页面（type=2，parent=系统管理）
  (11,  1,  '用户管理', 2, 'system:user',       'user',  1, '/user'),
  (12,  1,  '角色管理', 2, 'system:role',       'team',  2, '/role'),
  (13,  1,  '权限管理', 2, 'system:permission', 'lock',  3, '/permission');

INSERT IGNORE INTO sys_permission (id, parent_id, name, type, perms, icon, sort) VALUES
  -- 按钮（type=3，parent=分类管理；查询在前，与 system:*:list 同模式）
  (214, 21, '分类查询', 3, 'goods:category:list', NULL, 0),
  (211, 21, '分类新增', 3, 'goods:category:add',    NULL, 1),
  (212, 21, '分类编辑', 3, 'goods:category:edit',   NULL, 2),
  (213, 21, '分类删除', 3, 'goods:category:delete', NULL, 3),
  -- 按钮（type=3，parent=品牌管理）
  (224, 22, '品牌查询', 3, 'goods:brand:list',    NULL, 0),
  (221, 22, '品牌新增', 3, 'goods:brand:add',    NULL, 1),
  (222, 22, '品牌编辑', 3, 'goods:brand:edit',   NULL, 2),
  (223, 22, '品牌删除', 3, 'goods:brand:delete', NULL, 3),
  -- 按钮（type=3，parent=商品管理）
  (234, 23, '商品查询', 3, 'goods:spu:list',      NULL, 0),
  (231, 23, '商品新增', 3, 'goods:spu:add',    NULL, 1),
  (232, 23, '商品编辑', 3, 'goods:spu:edit',   NULL, 2),
  (233, 23, '商品删除', 3, 'goods:spu:delete', NULL, 3),
  -- 按钮（type=3，parent=用户管理）
  (111, 11, '用户查询', 3, 'system:user:list',   NULL, 1),
  (112, 11, '用户新增', 3, 'system:user:add',    NULL, 2),
  (113, 11, '用户编辑', 3, 'system:user:edit',   NULL, 3),
  (114, 11, '用户删除', 3, 'system:user:delete', NULL, 4),
  (115, 11, '分配角色', 3, 'system:user:assignRole', NULL, 5),
  -- 按钮（type=3，parent=角色管理）
  (121, 12, '角色查询', 3, 'system:role:list',   NULL, 1),
  (122, 12, '角色新增', 3, 'system:role:add',    NULL, 2),
  (123, 12, '角色编辑', 3, 'system:role:edit',   NULL, 3),
  (124, 12, '角色删除', 3, 'system:role:delete', NULL, 4),
  (125, 12, '分配用户', 3, 'system:role:assignUser', NULL, 5),
  (126, 12, '分配权限', 3, 'system:role:assignPermission', NULL, 6),
  -- 按钮（type=3，parent=权限管理）
  (131, 13, '权限查询', 3, 'system:permission:list',   NULL, 1),
  (132, 13, '权限新增', 3, 'system:permission:add',    NULL, 2),
  (133, 13, '权限编辑', 3, 'system:permission:edit',   NULL, 3),
  (134, 13, '权限删除', 3, 'system:permission:delete', NULL, 4);

-- 回填旧种子中页面缺失的路由地址（INSERT IGNORE 不改已存在行，仅补空值）
UPDATE sys_permission SET route = '/user'       WHERE id = 11 AND route IS NULL;
UPDATE sys_permission SET route = '/role'       WHERE id = 12 AND route IS NULL;
UPDATE sys_permission SET route = '/permission' WHERE id = 13 AND route IS NULL;

-- ============================================================
-- 幂等权限种子：店铺管理（平台 admin 后台，业务经 admin 端 BFF /admin/shop/** 编排落 store 域）
--   顶级目录(4) → 页面 店铺列表(41, 带路由 /shop，perms=store:shop) → 按钮 查询/审核
-- 角色授权见 db/backfill-store-permission.sql（自动补发给后端管理角色）或「角色管理→分配权限」UI。
-- ============================================================
INSERT IGNORE INTO sys_permission (id, parent_id, name, type, perms, icon, sort, route) VALUES
  (4,  0, '店铺管理', 1, NULL,        'shop', 0, NULL),
  (41, 4, '店铺列表', 2, 'store:shop', 'shop', 1, '/shop');

INSERT IGNORE INTO sys_permission (id, parent_id, name, type, perms, icon, sort) VALUES
  (411, 41, '店铺查询', 3, 'store:shop:list',  NULL, 0),
  (412, 41, '店铺审核', 3, 'store:shop:audit', NULL, 1);
