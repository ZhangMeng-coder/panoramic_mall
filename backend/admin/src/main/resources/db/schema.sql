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
-- 幂等权限种子：系统管理目录 → 用户/角色/权限 页面 → 功能按钮
-- 使用显式 ID + INSERT IGNORE，重复执行不会产生重复数据。
-- ============================================================
INSERT IGNORE INTO sys_permission (id, parent_id, name, type, perms, icon, sort) VALUES
  -- 目录（type=1，parent=0）
  (1,   0,  '系统管理', 1, NULL,             'setting', 1),
  -- 页面（type=2，parent=系统管理）
  (11,  1,  '用户管理', 2, 'system:user',       'user',     1),
  (12,  1,  '角色管理', 2, 'system:role',       'team',     2),
  (13,  1,  '权限管理', 2, 'system:permission', 'lock',     3),
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
