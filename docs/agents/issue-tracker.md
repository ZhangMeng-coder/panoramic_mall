# Issue tracker: Local Markdown

本仓库的 issue 与 spec 以 markdown 文件形式存放在 `.scratch/` 下。

## 约定

- 一个 feature 一个目录:`.scratch/<feature-slug>/`
- spec 为 `.scratch/<feature-slug>/spec.md`
- 实现工单每个 ticket 一个文件:`.scratch/<feature-slug>/issues/<NN>-<slug>.md`,编号自 `01` 起,**不合并成单个 tickets 文件**
- triage 状态写在每个 issue 文件靠顶部的 `Status:` 行(角色串见 `triage-labels.md`)
- 评论与对话历史追加在文件底部的 `## Comments` 标题下

## 技能说「publish to the issue tracker」时

在 `.scratch/<feature-slug>/` 下新建文件(目录不存在就先建)。

## 技能说「fetch the relevant ticket」时

读取该路径的文件。用户通常会直接给出路径或 issue 编号。

## Wayfinding 操作

供 `/wayfinder` 使用。**map** 是一个文件,每个 ticket 一个 **child** 文件。

- **Map**:`.scratch/<effort>/map.md`(承载 Notes / Decisions-so-far / Fog 正文)。
- **Child ticket**:`.scratch/<effort>/issues/NN-<slug>.md`,自 `01` 起,正文即问题。`Type:` 行记录 ticket 类型(`research`/`prototype`/`grilling`/`task`);`Status:` 行记录 `claimed`/`resolved`。
- **Blocking**:靠顶部一行 `Blocked by: NN, NN`。所列文件全部 `resolved` 即解除阻塞。
- **Frontier**:扫描 `.scratch/<effort>/issues/` 下 open、未阻塞、未认领的文件,编号最小者胜。
- **Claim**:开始任何工作前先置 `Status: claimed` 并保存。
- **Resolve**:在 `## Answer` 标题下追加答案,置 `Status: resolved`,再把上下文指针(gist + 链接)追加到 `map.md` 的 Decisions-so-far。
