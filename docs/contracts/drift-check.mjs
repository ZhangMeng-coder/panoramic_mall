#!/usr/bin/env node
/**
 * drift-check.mjs —— 契约漂移检查器
 * ----------------------------------------------------------------------------
 * 做一件事：把 docs/contracts/*.md 里登记的契约，与 backend/ 与 frontend/ 的
 * 源码做**纯文本级**对照，输出差集。
 *
 * ⚠ 本脚本**不启动任何服务、不打任何接口**，只读文件、只做文本比对。
 *    这与仓库 CLAUDE.md「代码验证只到编译通过」的口径一致。
 *
 * 用法：
 *   node docs/contracts/drift-check.mjs
 *   node docs/contracts/drift-check.mjs --root E:/workspace/panoramic_mall
 *
 * 退出码：0 = 无漂移；1 = 有漂移（错误）；仍会打印警告项。
 *
 * 检查项（对应 docs/contracts/README.md）：
 *   1  端点覆盖差集：controller 注解 ↔ 契约表（双向）
 *   1b 内部契约两端一致：Feign 客户端 ↔ 域实现
 *   2  权限串：代码 @PreAuthorize ↔ 契约表
 *   3  权限串：契约表 ⊆ sys_permission 种子；前端 v-perm ⊆ 契约表
 *   4  路由（前端 router ↔ 权限种子 route）—— 启发式，仅告警
 *   5  入出参类型存在性（在 typeDirs 下能找到 .java）
 *   6  形状哨兵：页面级必出现 RespData；域实现必不出现
 *   7  隐式契约哨兵（cross-cutting.md 的 contract-sentinels 块）
 *   8  网关：路由 / bff-services 白名单 / 两侧鉴权白名单
 *   9  Nacos：spring.config.import 不得带 optional:
 *
 * 依赖：Node 18+，零第三方依赖。
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/* ═══════════════════════════════════════════════════════════════════════════
   0. 基础设施
   ═══════════════════════════════════════════════════════════════════════════ */

const HERE = path.dirname(fileURLToPath(import.meta.url));
const argv = process.argv.slice(2);
const rootArg = argv.indexOf('--root');
const ROOT = rootArg >= 0 && argv[rootArg + 1]
  ? path.resolve(argv[rootArg + 1])
  : path.resolve(HERE, '..', '..');

const CONTRACTS_DIR = path.join(ROOT, 'docs', 'contracts');

const errors = [];
const warnings = [];
const notes = [];
/** sys_permission 种子里的权限串（由 loadSeedPerms 填充，须在契约循环前调用） */
const SEED_PERMS = new Set();

/** 记录一个错误（导致退出码 1） */
function fail(scope, msg) { errors.push(`[${scope}] ${msg}`); }
/** 记录一个警告（不影响退出码） */
function warn(scope, msg) { warnings.push(`[${scope}] ${msg}`); }

const rel = (p) => path.relative(ROOT, p).split(path.sep).join('/');

/** 递归收集文件 */
function walk(dir, exts) {
  const out = [];
  if (!fs.existsSync(dir)) return out;
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === 'node_modules' || e.name === 'target' || e.name === '.git') continue;
      out.push(...walk(p, exts));
    } else if (!exts || exts.some((x) => e.name.endsWith(x))) {
      out.push(p);
    }
  }
  return out;
}

/** 递归收集文本（用于哨兵字面量搜索），跳过二进制与大文件 */
function walkTextFiles(dir, exts) {
  return walk(dir, exts).filter((p) => {
    try { return fs.statSync(p).size < 2 * 1024 * 1024; } catch { return false; }
  });
}

const read = (p) => { try { return fs.readFileSync(p, 'utf8'); } catch { return ''; } };

/**
 * 把 Java/Vue 源码里的**注释内容替换成空格**（换行保留）。
 * ⚠ 必须做这一步：本仓库注释里大量出现注解样文本，例如「方法直接返回业务结果类型（不包 RespData）」
 *    或「{@code @PreAuthorize} 是唯一授权点」—— 不剥注释会把这些当成真代码。
 * 保留换行与字符长度，故**行号与列偏移完全不变**。
 */
function blankComments(text) {
  const chars = text.split('');
  let i = 0;
  const n = chars.length;
  const blank = (from, to) => {
    for (let k = from; k < to; k++) if (chars[k] !== '\n' && chars[k] !== '\r') chars[k] = ' ';
  };
  while (i < n) {
    const c = chars[i];
    const c2 = chars[i + 1];
    if (c === '/' && c2 === '/') {
      const start = i;
      while (i < n && chars[i] !== '\n') i++;
      blank(start, i);
    } else if (c === '/' && c2 === '*') {
      const start = i;
      i += 2;
      while (i < n && !(chars[i] === '*' && chars[i + 1] === '/')) i++;
      i = Math.min(n, i + 2);
      blank(start, i);
    } else if (c === '"' || c === "'") {
      const quote = c;
      i++;
      while (i < n) {
        if (chars[i] === '\\') { i += 2; continue; }
        if (chars[i] === quote) { i++; break; }
        if (chars[i] === '\n') break; // 未闭合，容错
        i++;
      }
    } else {
      i++;
    }
  }
  return chars.join('');
}

/** path 可能是文件也可能是目录；统一返回文件列表 */
function filesAt(p, exts) {
  const abs = path.join(ROOT, p);
  if (!fs.existsSync(abs)) return [];
  const st = fs.statSync(abs);
  if (st.isFile()) return exts && !exts.some((x) => abs.endsWith(x)) ? [] : [abs];
  return walkTextFiles(abs, exts);
}

/** 路径归一：前导 /、去尾斜杠、折叠重复斜杠；空串返回 '/' */
function normPath(p) {
  if (!p) return '/';
  let s = String(p).trim().replace(/\\/g, '/');
  s = s.replace(/\/{2,}/g, '/');
  if (!s.startsWith('/')) s = '/' + s;
  if (s.length > 1 && s.endsWith('/')) s = s.slice(0, -1);
  return s;
}

const lineOf = (text, idx) => text.slice(0, idx).split('\n').length;

/* ═══════════════════════════════════════════════════════════════════════════
   1. Markdown 解析：meta 块 / 表格 / 哨兵块
   ═══════════════════════════════════════════════════════════════════════════ */

function parseMeta(text) {
  const m = text.match(/<!--\s*contract-meta\s*([\s\S]*?)-->/);
  if (!m) return null;
  const meta = {};
  for (const line of m[1].split(/\r?\n/)) {
    const mm = line.match(/^\s*([A-Za-z][\w-]*)\s*:\s*(.+?)\s*$/);
    if (mm) meta[mm[1]] = mm[2];
  }
  return meta;
}

function parseSentinelBlock(text) {
  const m = text.match(/<!--\s*contract-sentinels\s*([\s\S]*?)-->/);
  if (!m) return null;
  try { return JSON.parse(m[1]); } catch (e) {
    fail('cross-cutting', `contract-sentinels 块不是合法 JSON：${e.message}`);
    return null;
  }
}

function splitRow(line) {
  return line.replace(/^\|/, '').replace(/\|$/, '').split('|')
    .map((s) => s.trim().replace(/`/g, '')); // 去掉行内代码反引号，便于与源码字面量比对
}

/** 解析所有 markdown 表格（跳过表头分隔行） */
function parseTables(text) {
  const lines = text.split(/\r?\n/);
  const tables = [];
  let cur = null;
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i].trim();
    if (!(raw.startsWith('|') && raw.endsWith('|'))) { cur = null; continue; }
    const cells = splitRow(raw);
    if (/^[\s|:-]+$/.test(raw) && cells.every((c) => /^:?-{2,}:?$/.test(c) || c === '')) continue;
    if (!cur) { cur = { header: cells, rows: [], line: i + 1 }; tables.push(cur); continue; }
    if (cells.length === cur.header.length) cur.rows.push({ cells, line: i + 1 });
    else cur = null;
  }
  return tables;
}

/** 按表头列名取列下标；全部命中才返回，否则 null */
function columnIndex(table, names) {
  const idx = {};
  for (const n of names) {
    const i = table.header.indexOf(n);
    if (i < 0) return null;
    idx[n] = i;
  }
  return idx;
}

/* ═══════════════════════════════════════════════════════════════════════════
   2. Java 源码解析：映射注解 / @PreAuthorize / FeignClient
   ═══════════════════════════════════════════════════════════════════════════ */

const MAPPING_RE = /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)(\s*\(([^)]*)\))?/g;
const VERB_OF = {
  GetMapping: 'GET', PostMapping: 'POST', PutMapping: 'PUT',
  DeleteMapping: 'DELETE', PatchMapping: 'PATCH',
};

/** 从注解参数串里取第一个字符串字面量作为路径 */
function pathFromArgs(args) {
  if (!args) return '';
  const m = args.match(/"([^"]*)"/);
  return m ? m[1] : '';
}

/**
 * 解析一个 Controller（或 Feign 接口）的端点。
 * classPrefix：页面/域 Controller 取类级 @RequestMapping；Feign 客户端传 ''
 */
function extractEndpoints(file) {
  const name = path.basename(file);
  const text = blankComments(read(file)); // ⚠ 先剥注释，行号与偏移不变
  const isFeign = /@FeignClient\b/.test(text);

  // 类声明位置（class / interface）
  const clsMatch = text.match(/\b(?:public\s+)?(?:final\s+)?(?:abstract\s+)?(?:class|interface)\s+(\w+)/);
  const clsIdx = clsMatch ? clsMatch.index : 0;

  // 收集所有映射注解
  const all = [];
  MAPPING_RE.lastIndex = 0;
  let m;
  while ((m = MAPPING_RE.exec(text)) !== null) {
    const ann = m[1];
    const args = m[3] || '';
    let verb = VERB_OF[ann];
    if (!verb) {
      const mm = args.match(/RequestMethod\.(\w+)/);
      verb = mm ? mm[1].toUpperCase() : 'ANY';
    }
    all.push({ ann, args, verb, idx: m.index, line: lineOf(text, m.index), p: pathFromArgs(args) });
  }

  // 类级前缀：class 声明之前的最后一个 @RequestMapping
  let classPrefix = '';
  if (isFeign) {
    const fc = text.match(/@FeignClient\s*\(([\s\S]*?)\)/);
    const pm = fc && fc[1].match(/path\s*=\s*"([^"]*)"/);
    classPrefix = pm ? pm[1] : '';
  } else {
    const before = all.filter((x) => x.idx < clsIdx && x.ann === 'RequestMapping');
    classPrefix = before.length ? before[before.length - 1].p : '';
  }

  // 类级注解本身不算端点：页面/域里 idx < clsIdx 的 RequestMapping 是类级
  const methods = all.filter((x) => (isFeign ? true : x.idx > clsIdx));

  // ── 权限串归属：每个 @PreAuthorize 就近挂到**距离最近**的映射注解上。
  //    比"前一段/后一段"窗口更稳：写在 @GetMapping 之前或之后都能正确归属，
  //    也不会把上一个方法的权限串错算给下一个（PermissionController 的 /menus 曾因此误报）。
  //    写在类声明之前的视为**类级授权**，作为没有自己权限串的方法的兜底。
  const permsOf = new Map(methods.map((x) => [x, new Set()]));
  const classPerms = new Set();
  for (const occ of text.matchAll(/@PreAuthorize\s*\(([^)]*)\)/g)) {
    const found = [...occ[1].matchAll(/'([^']+)'|"([^"]+)"/g)]
      .map((x) => x[1] || x[2])
      .filter((s) => /^[a-z][a-z0-9]*(:[a-z][a-z0-9]*){1,3}$/i.test(s));
    if (!found.length) continue;
    if (occ.index < clsIdx) { found.forEach((f) => classPerms.add(f)); continue; }
    let best = null;
    let bestD = Infinity;
    for (const mp of methods) {
      const d = Math.abs(occ.index - mp.idx);
      if (d < bestD) { bestD = d; best = mp; }
    }
    if (best) found.forEach((f) => permsOf.get(best).add(f));
  }

  const endpoints = methods.map((cur) => {
    const own = permsOf.get(cur);
    const permSet = own.size ? own : classPerms;
    return {
      verb: cur.verb,
      path: isFeign ? normPath(cur.p) : normPath(classPrefix + '/' + cur.p),
      perm: permSet.size ? [...permSet].join('|') : '—',
      line: cur.line,
      file: name,
    };
  });
  return { name, isFeign, classPrefix, endpoints, text };
}

/** 扫描目录下所有 Controller 的端点 */
function scanEndpoints(dirs, exts = ['.java']) {
  const out = [];
  for (const d of dirs) {
    for (const f of walk(path.join(ROOT, d), exts)) {
      const r = extractEndpoints(f);
      if (!r.endpoints.length) continue;
      out.push({ file: rel(f), ...r });
    }
  }
  return out;
}

/** 建立「类型名 → 文件路径」索引，用于第 5 项检查 */
function buildTypeIndex(typeDirs) {
  const index = new Map();
  for (const d of typeDirs) {
    for (const f of walk(path.join(ROOT, d), ['.java'])) {
      const base = path.basename(f, '.java');
      if (!index.has(base)) index.set(base, rel(f));
    }
  }
  return index;
}

const TYPE_STOP = new Set([
  'void', 'Void', 'Long', 'String', 'Integer', 'Boolean', 'Double', 'Float',
  'Short', 'Byte', 'Character', 'Object', 'Number', 'Map', 'List', 'Set',
  'Collection', 'Optional', 'BigDecimal', 'LocalDate', 'LocalDateTime', 'Date',
  'RespData', 'PageResult', 'Mono', 'Flux', 'Iterable',
]);

/** 从表格单元格里抽取类型名 */
function typesIn(cell) {
  if (!cell || cell === '—') return [];
  const out = [];
  for (const m of cell.matchAll(/[A-Za-z_][\w$]*(?:\.[A-Za-z_][\w$]*)*/g)) {
    const t = m[0];
    if (TYPE_STOP.has(t)) continue;
    if (/^(java|javax|jakarta|com\.panoramic)/.test(t)) continue;
    if (!/^[A-Z]/.test(t)) continue;
    out.push(t);
  }
  return [...new Set(out)];
}

/* ═══════════════════════════════════════════════════════════════════════════
   3. 逐契约文件检查
   ═══════════════════════════════════════════════════════════════════════════ */

const contractFiles = fs.existsSync(CONTRACTS_DIR)
  ? fs.readdirSync(CONTRACTS_DIR).filter((f) => f.endsWith('.md')).map((f) => path.join(CONTRACTS_DIR, f))
  : [];

const summary = [];
/** 所有登记在册的权限串（供第 3 项"前端 v-perm ⊆ 契约表"用） */
const allTablePerms = new Set();

// ⚠ 权限种子必须在契约循环**之前**装载：循环里会用它判定「权限串是否在种子里」
const sqlCount = loadSeedPerms();

for (const file of contractFiles) {
  const text = read(file);
  const meta = parseMeta(text);
  const scope = path.basename(file, '.md');

  // ── 哨兵块：只 cross-cutting.md 有
  if (scope === 'cross-cutting') {
    const s = parseSentinelBlock(text);
    if (s) checkSentinels(s, scope);
    else warn(scope, '未找到 contract-sentinels 块，哨兵检查已跳过');
    continue;
  }

  if (!meta) {
    // 占位文件（待建服务）没有可扫的源，安静跳过
    const isPlaceholder = /待建/.test(text);
    if (!isPlaceholder) warn(scope, '缺少 contract-meta 块，未纳入检查');
    else notes.push(`${scope}：待建占位，未纳入检查`);
    continue;
  }

  if (meta.layer === 'gateway') {
    checkGateway(meta, text, scope);
    continue;
  }

  // ── 页面级 / 内部 Feign
  const isInternal = meta.layer === 'internal';
  const internalCols = ['Feign 方法', '方法', '路径', '入参', '出参', '契约声明(接口模块)', '域实现', '调用方', '状态'];

  // 收集该文件里所有符合列名的表
  const tables = [];
  for (const t of parseTables(text)) {
    const wanted = isInternal ? internalCols : ['方法', '路径', '权限串', '入参', '出参', '声明位置', '状态'];
    const idx = columnIndex(t, wanted);
    if (idx) tables.push({ t, idx });
  }

  if (!tables.length) {
    if (!/待建/.test(text)) fail(scope, '找到了 contract-meta 但没有可解析的契约表（列名是否被改动？有无「状态」列？）');
    continue;
  }

  const allRows = [];
  for (const { t, idx } of tables) {
    for (const r of t.rows) {
      const get = (k) => (idx[k] !== undefined ? r.cells[idx[k]] : '');
      const verb = get('方法').toUpperCase();
      if (!/^(GET|POST|PUT|DELETE|PATCH|ANY)$/.test(verb)) continue;

      // 「状态」列：留空 = 已实现。只认「已实现」「待实现」两个显式值——
      // 打错字（如「待实线」「TODO」）必须报错，否则会被当成"已实现"而静默失效。
      const raw = get('状态').replace(/`/g, '').trim();
      if (raw !== '' && raw !== '已实现' && raw !== '待实现') {
        fail(scope, `「状态」列只能留空 / 已实现 / 待实现，实际是「${raw}」（表第 ${r.line} 行 ${verb} ${get('路径')}）`);
      }

      allRows.push({
        feignName: get('Feign 方法'),
        verb,
        path: normPath(get('路径')),
        perm: isInternal ? '—' : (get('权限串') || '—'),
        inTypes: typesIn(get('入参')),
        outTypes: typesIn(get('出参')),
        status: raw === '待实现' ? '待实现' : '已实现',
        line: r.line,
      });
    }
  }

  if (!allRows.length) { fail(scope, '契约表解析出 0 行'); continue; }

  // 待实现行 = 契约先行的产物（接口已定、代码还没写）。只做存在性登记，
  // 不参与「契约 ↔ 代码」双向核对，也不查类型 / 权限种子（DTO 与种子可能同样还没写）。
  const pendRows = allRows.filter((r) => r.status === '待实现');
  const rows = allRows.filter((r) => r.status !== '待实现');

  const pendSet = new Set(pendRows.map((r) => `${r.verb} ${r.path}`));
  if (!isInternal) rows.forEach((r) => r.perm !== '—' && allTablePerms.add(r.perm));

  const tableSet = new Set(rows.map((r) => `${r.verb} ${r.path}`));
  for (const k of pendSet) {
    if (tableSet.has(k)) fail(scope, `同一接口既有已实现行又有待实现行，删掉待实现那条：${k}`);
  }
  const fileErrsBefore = errors.length;

  // ── 第 1 项（页面级）：源码端点 ↔ 契约表
  if (!isInternal) {
    const src = scanEndpoints([meta.scanDirs]);
    const srcSet = new Map();
    for (const c of src) for (const e of c.endpoints) srcSet.set(`${e.verb} ${e.path}`, { ...e, file: c.file });

    for (const k of srcSet.keys()) {
      if (tableSet.has(k)) continue;
      // 标了「待实现」但代码已经写出来 = 标记腐烂，必须摘（这是待实现机制的反向哨兵）
      if (pendSet.has(k)) fail(scope, `标记为待实现，但代码里已有该接口，请摘掉标记：${k}（${srcSet.get(k).file}:${srcSet.get(k).line}）`);
      else fail(scope, `代码有、契约表没有：${k}（${srcSet.get(k).file}:${srcSet.get(k).line}）`);
    }
    for (const k of tableSet) if (!srcSet.has(k)) fail(scope, `契约表有、代码没有（幽灵行）：${k}（${path.basename(file)}:${rows.find((r) => `${r.verb} ${r.path}` === k).line}）`);

    // ── 第 2 项：权限串 代码 ↔ 表
    for (const r of rows) {
      const key = `${r.verb} ${r.path}`;
      const s = srcSet.get(key);
      if (!s) continue;
      if (s.perm !== r.perm) fail(scope, `权限串不一致 ${key}：契约表=${r.perm} 代码=${s.perm}`);
    }

    summary.push({ scope, layer: meta.layer, table: allRows.length, pending: pendRows.length, code: srcSet.size });
  }

  // ── 第 1b 项（内部）：Feign 客户端 ↔ 域实现
  if (isInternal) {
    if (!meta.feignClient) { fail(scope, '缺少 contract-meta: feignClient'); continue; }
    if (!meta.implScanDirs) { fail(scope, '缺少 contract-meta: implScanDirs'); continue; }

    const feign = extractEndpoints(path.join(ROOT, meta.feignClient));
    const feignSet = new Set(feign.endpoints.map((e) => `${e.verb} ${e.path}`));

    const implAll = scanEndpoints([meta.implScanDirs]);
    const basePath = meta.basePath || '';
    const implSet = new Map();
    for (const c of implAll) {
      for (const e of c.endpoints) {
        const stripped = basePath && e.path.startsWith(basePath)
          ? normPath(e.path.slice(basePath.length))
          : e.path;
        implSet.set(`${e.verb} ${stripped}`, { ...e, raw: e.path, file: c.file });
      }
    }

    // 契约表 ↔ Feign 声明
    for (const k of feignSet) {
      if (tableSet.has(k)) continue;
      // 同上：标了「待实现」但 Feign 声明已写出来 = 标记腐烂
      if (pendSet.has(k)) fail(scope, `标记为待实现，但 Feign 声明里已有该接口，请摘掉标记：${k}`);
      else fail(scope, `Feign 客户端有、契约表没有：${k}`);
    }
    for (const k of tableSet) if (!feignSet.has(k)) fail(scope, `契约表有、Feign 客户端没有：${k}`);

    // Feign 声明 ↔ 域实现（两端一致）
    for (const k of feignSet) if (!implSet.has(k)) fail(scope, `Feign 声明有、域实现没有：${k}（域侧是否改了类级 @RequestMapping 前缀？）`);
    for (const k of implSet.keys()) if (!feignSet.has(k)) fail(scope, `域实现有、Feign 声明没有：${k}（域侧未使用端点：${implSet.get(k).raw}）`);

    // 第 6 项反向哨兵：域实现不得出现 RespData
    for (const c of implAll) {
      if (/RespData/.test(c.text)) fail(scope, `域实现 ${c.file} 出现了 RespData —— 域内接口必须直接返回业务类型`);
    }

    summary.push({ scope, layer: meta.layer, table: allRows.length, pending: pendRows.length, code: implSet.size, feign: feignSet.size });
  }

  // ── 第 3 项：权限串 ⊆ sys_permission 种子；前端 v-perm ⊆ 契约表
  for (const r of rows) {
    if (r.perm !== '—' && !SEED_PERMS.has(r.perm)) {
      fail(scope, `权限串不在 sys_permission 种子里：${r.perm}（${r.verb} ${r.path}）`);
    }
  }

  // ── 第 5 项：类型存在性
  if (meta.typeDirs) {
    const typeIndex = buildTypeIndex(meta.typeDirs.split(',').map((s) => s.trim()));
    for (const r of rows) {
      for (const t of [...r.inTypes, ...r.outTypes]) {
        if (!typeIndex.has(t)) fail(scope, `类型找不到：${t}（${r.verb} ${r.path}）`);
      }
    }
  }

  // ── 第 6 项（页面级正向哨兵）：剥注释后再判，避免认注释里提到的 RespData
  if (!isInternal && meta.scanDirs) {
    for (const f of walk(path.join(ROOT, meta.scanDirs), ['.java'])) {
      const code = blankComments(read(f));
      if (!/@RestController/.test(code)) continue;
      if (!/RespData/.test(code)) fail(scope, `页面级 Controller ${rel(f)} 未出现 RespData`);
    }
  }

  if (errors.length === fileErrsBefore) {
    notes.push(`${scope}：与代码一致${pendRows.length ? `（另有 ${pendRows.length} 条待实现，未参与核对）` : ''}`);
  }
}

/* ═══════════════════════════════════════════════════════════════════════════
   4. 各类专项检查
   ═══════════════════════════════════════════════════════════════════════════ */

/* ── 第 7 项：隐式契约哨兵 ─────────────────────────────────────────────── */
function checkSentinels(spec, scope) {
  for (const item of spec.presence || []) {
    for (const dir of item.in || []) {
      const files = filesAt(dir, null);
      const hit = files.some((f) => read(f).includes(item.literal));
      if (!hit) fail(scope, `哨兵缺失：字面量「${item.literal}」未出现在 ${dir}（${item.why}）`);
    }
  }
  for (const item of spec.absence || []) {
    const exts = item.ext || null;
    for (const dir of item.in || []) {
      for (const f of filesAt(dir, exts)) {
        if (read(f).includes(item.literal)) fail(scope, `已删除契约复活：「${item.literal}」出现在 ${rel(f)}（${item.why}）`);
      }
    }
  }
}

/* ── 第 8 项：网关 ──────────────────────────────────────────────────────── */
function checkGateway(meta, text, scope) {
  const yml = read(path.join(ROOT, meta.config));
  if (!yml) { fail(scope, `读不到网关配置：${meta.config}`); return; }

  // 路由断言 ↔ gateway.md 路由表
  // 两侧归一：yml 侧去掉 YAML 转义，文档侧去掉行内反引号与 `Path=` 前缀
  const gwPath = (s) => String(s).replace(/`/g, '').replace(/^Path=/, '').replace(/\\/g, '').trim();
  const ymlPreds = new Set([...yml.matchAll(/Path=([^\s,\]]+)/g)].map((m) => gwPath(m[1])));
  const tables = parseTables(text);
  const routeTbl = tables.find((t) => t.header.includes('断言路径'));
  const docPreds = new Set(routeTbl ? routeTbl.rows.map((r) => gwPath(r.cells[routeTbl.header.indexOf('断言路径')])) : []);

  for (const p of ymlPreds) if (!docPreds.has(p)) fail(scope, `路由有、契约页没有：${p}`);
  for (const p of docPreds) if (!ymlPreds.has(p)) fail(scope, `契约页有、路由没有：${p}`);

  // bff-services 白名单
  const bff = yml.match(/bff-services\s*:\s*(\S+)/);
  const bffVal = bff ? bff[1] : '';
  if (bffVal) {
    const inDoc = text.includes(bffVal);
    if (!inDoc) warn(scope, `bff-services 当前值「${bffVal}」未出现在契约页中`);
    // 白名单里的服务是否真实存在
    for (const svc of bffVal.split(',').map((s) => s.trim()).filter(Boolean)) {
      if (!fs.existsSync(path.join(ROOT, 'backend', svc))) fail(scope, `白名单里的「${svc}」在 backend/ 下不存在`);
    }
  }

  // 鉴权白名单：网关侧 vs 契约页
  const wl = yml.match(/whitelist-paths\s*:\s*(.+)/);
  const gwPaths = wl ? wl[1].split(',').map((s) => s.trim()).filter(Boolean) : [];
  for (const p of gwPaths) if (!text.includes(p)) fail(scope, `网关白名单有、契约页没有：${p}`);

  // 路由块：以 "- id:" 切分，每块取 `uri: lb://<svc>` 与 `Path=/<前缀>/**`
  const routeTargets = yml.split(/\n\s*-\s*id:\s*/).slice(1).map((blk) => {
    const u = blk.match(/uri:\s*lb:\/\/([A-Za-z0-9._-]+)/);
    if (!u) return null;
    const p = blk.match(/Path=(\/[A-Za-z0-9._/-]*?)\/\*\*/);
    return { svc: u[1], prefix: p ? gwPath(p[1]) : null };
  }).filter(Boolean);

  // 端 BFF 名单**从 `bff-services` 的值推导**（不硬编码），路由前缀从路由块推导——
  // 否则新增一个端 BFF 只在网关加路由时，本项会静默跳过它的两侧白名单交叉核对。
  const svcList = bffVal ? bffVal.split(',').map((s) => s.trim()).filter(Boolean) : [];
  const prefixOf = {};
  for (const svc of svcList) {
    const hits = routeTargets.filter((r) => r.svc === svc);
    if (hits.length !== 1 || !hits[0].prefix) {
      fail(scope, `无法为端 BFF「${svc}」推出唯一的路由前缀（uri: lb://${svc} ↔ Path=/<前缀>/**，命中 ${hits.length} 条）`
        + `——两侧白名单交叉核对需要它，故直接失败，不允许静默跳过`);
      continue;
    }
    prefixOf[svc] = hits[0].prefix;
  }
  // 反向哨兵：路由的转发目标不在 bff-services 里 = 死配置（经网关一律 403）
  for (const r of routeTargets) {
    if (svcList.length && !svcList.includes(r.svc)) {
      warn(scope, `路由 lb://${r.svc} 的转发目标不在 bff-services 里——该路由经网关一律 403（死配置）`);
    }
  }

  // 两侧白名单互为子集（网关带前缀、服务不带）
  const norm = (p, prefix) => {
    let s = p;
    if (prefix && s.startsWith(prefix)) s = s.slice(prefix.length) || '/';
    return normPath(s);
  };
  for (const svc of Object.keys(prefixOf)) {
    const localYml = path.join(ROOT, 'backend', svc, 'src', 'main', 'resources', 'application.yml');
    if (!fs.existsSync(localYml)) {
      fail(scope, `端 BFF「${svc}」的服务侧 application.yml 不存在（${rel(localYml)}）——两侧白名单无法核对`);
      continue;
    }
    const m2 = (read(localYml) || '').match(/whitelist-paths\s*:\s*(.+)/);
    const list = m2 ? m2[1].split(',').map((s) => s.trim()).filter(Boolean) : [];
    const prefix = prefixOf[svc];
    const gwForSvc = new Set(gwPaths.filter((p) => p.startsWith(prefix)).map((p) => norm(p, prefix)));
    for (const p of list) {
      const np = normPath(p);
      if (![...gwForSvc].some((g) => g === np || (g.endsWith('/**') && np.startsWith(g.slice(0, -3))))) {
        fail(scope, `${svc} 本地白名单「${p}」在网关侧白名单里没有对应项（免鉴权路径需两处各写一份）`);
      }
    }
  }

  summary.push({ scope, layer: 'gateway', table: docPreds.size, code: ymlPreds.size });
}

/* ── 第 9 项：Nacos import 不得带 optional: ───────────────────────────── */
function checkNacosImports() {
  const scope = 'nacos';
  const ymls = walk(path.join(ROOT, 'backend'), ['.yml', '.yaml'])
    .filter((f) => f.includes(`${path.sep}src${path.sep}main${path.sep}resources${path.sep}application`));
  if (!ymls.length) { warn(scope, '未找到任何 application.yml'); return; }
  for (const f of ymls) {
    const t = read(f);
    if (/config\s*:\s*[\s\S]{0,400}?import\s*:[\s\S]{0,400}?optional:/.test(t)) {
      fail(scope, `${rel(f)} 的 spring.config.import 带 optional: —— 共享配置缺失应当启动失败`);
    }
  }
  notes.push(`${scope}：${ymls.length} 个 application.yml 的 config.import 均未带 optional:`);
}

/* ── 第 3/4 项：权限种子与前端 ─────────────────────────────────────────── */
function loadSeedPerms() {
  const dir = path.join(ROOT, 'backend', 'admin', 'src', 'main', 'resources', 'db');
  const sqls = walk(dir, ['.sql']);
  for (const f of sqls) {
    for (const m of read(f).matchAll(/'([a-z][a-z0-9]*(?::[a-z][a-z0-9]*){1,3})'/gi)) {
      const v = m[1];
      if (v.includes(':') && !/^\d/.test(v)) SEED_PERMS.add(v);
    }
  }
  return sqls.length;
}

function checkFrontendPerms() {
  const scope = 'frontend';
  const dir = path.join(ROOT, 'frontend', 'admin', 'src');
  const files = walk(dir, ['.vue', '.js']);
  const seen = new Map();
  for (const f of files) {
    // 跳过指令自身的实现文件：它的注释里含 v-perm 的**用法示例**，不是真实调用点
    if (rel(f).includes('directives/perm.js')) continue;
    const t = read(f);
    for (const m of t.matchAll(/v-perm\s*=\s*(["'])((?:\\.|(?!\1).)*)\1/g)) {
      // 支持两种形态：v-perm="'a:b:c'" 与 v-perm="['a:b:c','d:e:f']"
      const inner = m[2];
      const vals = [...inner.matchAll(/'([^']+)'|"([^"]+)"/g)].map((x) => x[1] || x[2]);
      const list = vals.length ? vals : [inner.replace(/^['"]|['"]$/g, '')];
      for (const v of list) {
        if (!v) continue;
        if (!seen.has(v)) seen.set(v, rel(f));
      }
    }
  }
  for (const [v, f] of seen) {
    if (!allTablePerms.has(v) && !SEED_PERMS.has(v)) {
      fail(scope, `v-perm 的权限串「${v}」（${f}）既不在契约表也不在权限种子里`);
    } else if (!allTablePerms.has(v)) {
      warn(scope, `v-perm 的权限串「${v}」（${f}）在权限种子里但未登记进契约表`);
    }
  }
  notes.push(`${scope}：扫描 ${files.length} 个前端文件，发现 ${seen.size} 个 v-perm 权限串`);
}

/* ── 第 4 项：前端路由 ↔ 权限种子 route ───────────────────────────────── */

// 不需要权限种子 route 的静态路由：登录页与首页是「登录后必达」，不挂 RBAC 菜单。
// ⚠ 首页权限已于 2026-09-05 从权限表移除，故 /home 无种子项是预期。
// 这里显式列出而不是放宽带判断——永久噪音会训练人忽略警告，等于检查器失效。
// 注：跳过计数除本表两项外，还含两个「结构性条目」（根路径 `/` 与通配兜底 `/:xxx`）——它们同样不该
// 有种子，但不是静态路由，故统计文案分列，免得读者误以为本表放行了 4 条。
const STATIC_ROUTES = new Set(['/login', '/home']);

function checkRoutes() {
  const scope = 'routes';
  const dbDir = path.join(ROOT, 'backend', 'admin', 'src', 'main', 'resources', 'db');
  const seedRoutes = new Set();
  for (const f of walk(dbDir, ['.sql'])) {
    for (const m of read(f).matchAll(/'(\/[A-Za-z][\w/-]*)'/g)) seedRoutes.add(m[1]);
  }
  // ⚠ 路由文件按扩展名探测（前端已于 2026-09-14 全线转 TS，此前后缀写死成 .js 时本项会静默退化成
  // 「只告警不检查」——检查器跑了个空，等于失效）。探测不到一律 fail，不降级为警告：找不到输入
  // 说明本项没查，不能算通过。
  const routerDir = path.join(ROOT, 'frontend', 'admin', 'src', 'router');
  const routerFile = ['index.ts', 'index.js']
    .map((f) => path.join(routerDir, f))
    .find((f) => fs.existsSync(f));
  if (!routerFile) {
    fail(scope, `未找到 frontend/admin/src/router/index.{ts,js} —— 本项检查无法执行（前端路由与权限种子的一致性未被核对）`);
    return;
  }
  const routerRoutes = new Set([...read(routerFile).matchAll(/path\s*:\s*'([^']+)'/g)].map((m) => m[1]));
  let skippedStatic = 0;
  let skippedStructural = 0;
  for (const p of routerRoutes) {
    // 结构性条目：根路径 `/` 与通配兜底 `/:xxx`（如 `/:pathMatch(.*)*`）——不挂 RBAC、不该有种子，
    // 与上面的 STATIC_ROUTES 是两回事，分别计数免得统计文案把两者混为一谈。
    if (p === '/' || p.startsWith('/:')) { skippedStructural++; continue; }
    if (STATIC_ROUTES.has(p)) { skippedStatic++; continue; }
    const base = p.replace(/\/:[\w]+$/, '');
    if (!seedRoutes.has(p) && !seedRoutes.has(base)) {
      warn(scope, `前端路由「${p}」在 sys_permission.route 种子里没有对应项（详情页类路由可忽略）`);
    }
  }
  notes.push(
    `${scope}：${rel(routerFile)} 的 ${routerRoutes.size} 条路由 ↔ 种子 ${seedRoutes.size} 条 route（启发式，仅告警；`
    + `跳过静态路由 ${skippedStatic} 条、结构性条目 ${skippedStructural} 条）`,
  );
}

/* ═══════════════════════════════════════════════════════════════════════════
   5. 主流程
   ═══════════════════════════════════════════════════════════════════════════ */

checkNacosImports();
checkFrontendPerms();
checkRoutes();

/* ── 输出 ─────────────────────────────────────────────────────────────── */
const line = '─'.repeat(72);
console.log(`\n契约漂移检查 — 全景商城`);
console.log(`扫描根：${ROOT}`);
console.log(line);

if (summary.length) {
  console.log('登记与代码对照：');
  for (const s of summary) {
    const extra = (s.feign !== undefined ? ` / Feign ${s.feign}` : '')
      + (s.pending ? ` / 待实现 ${s.pending}` : '');
    console.log(`  · ${s.scope.padEnd(14)} ${String(s.layer).padEnd(9)} 契约 ${String(s.table).padStart(3)} 条 / 代码 ${String(s.code).padStart(3)} 条${extra}`);
  }
} else {
  console.log('（没解析到任何带 contract-meta 的契约文件）');
}

if (notes.length) {
  console.log(line);
  console.log('信息：');
  notes.forEach((n) => console.log(`  i ${n}`));
}

if (warnings.length) {
  console.log(line);
  console.log(`警告（${warnings.length}）：`);
  warnings.forEach((w) => console.log(`  ! ${w}`));
}

console.log(line);
if (errors.length) {
  console.log(`✗ 发现 ${errors.length} 处契约漂移：`);
  errors.forEach((e) => console.log(`  ✗ ${e}`));
  console.log(`\n退出码 1。修正后重跑：node docs/contracts/drift-check.mjs\n`);
  process.exit(1);
} else {
  console.log(`✓ 无契约漂移（权限种子 ${SEED_PERMS.size} 条 / SQL 文件 ${sqlCount} 个）\n`);
  process.exit(0);
}
