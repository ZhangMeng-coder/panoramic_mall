-- ============================================================
-- 全景商城 商品中台「查询」按钮存量回填（可重复执行）
--
-- 背景：给 分类/品牌/商品 三页新增了「查询」按钮（goods:*:list，
-- id 214/224/234）后，商品中台读接口改认按钮级 :list 权限。
-- 迁移前已持有 goods 页面(id 21/22/23)的角色并不持有新按钮，
-- 会导致迁移后失去列表/详情查看能力。本脚本幂等补发：
--   凡持有某 goods 页面权限的角色，自动补发该页面下对应的「查询」按钮。
--
-- 注意：登录时权限快照进 Redis，脚本执行后相关用户需重新登录才生效。
-- 执行库：panoramic_mall（先于或同于 schema.sql 再次执行均可，INSERT IGNORE 幂等）
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT rp.role_id, q.id
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
JOIN sys_permission q ON q.parent_id = p.id
                     AND q.perms IN ('goods:category:list', 'goods:brand:list', 'goods:spu:list')
WHERE p.id IN (21, 22, 23);
