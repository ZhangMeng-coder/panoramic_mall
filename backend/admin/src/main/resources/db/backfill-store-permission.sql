-- ============================================================
-- 全景商城 admin 后台「店铺管理」权限授权回填（可重复执行）
--
-- 背景：新增 店铺管理 目录(4)/页面 店铺列表(41)/按钮 店铺查询(411 store:shop:list)、
-- 店铺审核(412 store:shop:audit) 后，需授权给负责店铺管理/审核的角色。
-- 本脚本仿 backfill-query-button.sql 的“按角色已持权限补发”约定，幂等补发：
--   凡已持有 系统管理(id 11 用户管理 / 12 角色管理 / 13 权限管理) 或
--   商品中台(id 21 分类管理 / 22 品牌管理 / 23 商品管理) 任一页面权限的后端管理角色，
--   自动补发 店铺管理 页面(41) 与 查询/审核(411/412) 权限。
--
-- 如需细粒度拆分（审核员只给审核不给查询等），请用「角色管理 → 分配权限」UI 自行勾选，
-- 本脚本仅保证超管/后端管理角色默认可用。
-- 注意：登录时权限快照进 Redis，脚本执行后相关用户需重新登录才生效。
-- 执行库：panoramic_mall（先于或同于 schema.sql 再次执行均可，INSERT IGNORE 幂等）
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT rp.role_id, sp.id
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
JOIN sys_permission sp ON sp.id IN (41, 411, 412)
WHERE p.id IN (11, 12, 13, 21, 22, 23);
