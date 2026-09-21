# Domain Docs

工程类技能在探查代码库时应如何消费本仓库的领域文档。

## 探查前先读这些

- 仓库根部的 **`CONTEXT.md`**,或
- 若存在 **`CONTEXT-MAP.md`**:它指向每个 context 一份 `CONTEXT.md`,读其中与当前话题相关的每一份。
- **`docs/adr/`**:读与你要动的区域相关的 ADR。多 context 仓库还要看 `src/<context>/docs/adr/` 下的 context 局部决策。

**这些文件不存在就静默跳过** —— 不要指出缺失、不要建议提前创建。它们由 `/domain-modeling` 技能(经 `/grill-with-docs` 与 `/improve-codebase-architecture` 到达)在术语或决策真正定下时惰性创建。

## 文件结构

本仓库为 **single-context**:

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

(多 context 形态:根有 `CONTEXT-MAP.md`,各 `src/<context>/` 下自带 `CONTEXT.md` 与 `docs/adr/`。本仓库未采用。)

## 用词表里的词汇

输出中命名领域概念时(issue 标题、重构提案、假设、测试名),使用 `CONTEXT.md` 里定义的词,不要漂到词表明确回避的同义词。

需要的概念词表里还没有,这本身是个信号:要么你在发明项目不用的语言(重新考虑),要么存在真实空缺(记下来交 `/domain-modeling`)。

## 标出 ADR 冲突

输出与既有 ADR 矛盾时**显式点出**,不要静默覆盖:

> _与 ADR-0007(事件溯源订单)冲突,但值得重开,因为……_
