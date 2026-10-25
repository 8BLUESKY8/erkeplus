# 公用组件

页面里凡是「同一件事各写一遍」的，都往这里收。新增页面前先看这张表，别再造第三份。

| 组件 | 位置 | 用途 | 谁在用 |
| --- | --- | --- | --- |
| `SecondaryPage` | `ui/SecondaryPage.kt` | 二级页外壳：入场/退场动画、预测性返回、正文等入场结束再组合 | 所有覆盖层页面 |
| `PageLoading` | `ui/PageLoading.kt` | 整页加载（Material 默认规格，圈更粗） | 外壳 + 所有会联网的页面 |
| `InlineLoading` | `ui/PageLoading.kt` | 行内小圈，尺寸按场景给 | 按钮、翻页、搜索进度 |
| `SectionCard` | `ui/Common.kt` | 18dp 圆角 + 1dp 描边的分区卡片，可带标题和右侧计数 | 活动详情、组织详情 |
| `InfoRow` | `ui/Common.kt` | 左标签右值的一行，标签宽度可调 | 活动详情、组织详情 |
| `EmptyState` | `ui/Common.kt` | 居中一句话的空态 | 列表页 |
| `ExpandableText` | `ui/Collapse.kt` | 按行折的长文本，只有真溢出才出「展开全文」 | 组织简介 |
| `CollapsibleItems` | `ui/Collapse.kt` | 按条折的列表 | 负责人、主办活动 |
| `EntryRow` | `ui/EntryRow.kt` | 「图标 + 标题 + 副标题 + 右箭头」的设置行 | 我的、更多 |

## 约定

- **不要在页面里直接写 `CircularProgressIndicator`、`Card`、`InfoRow` 的同款实现**，样式一改就得挨个找。
- 卡片外边距由调用方通过 `modifier` 给（不同页面留白不同），组件内部只负责圆角、描边、底色和内边距。
- 页面特有的尺寸差异（比如活动详情的标签列窄一些）用组件参数或一次薄委托解决，不要复制一份实现。

## 还没收的

- 顶部「返回 + 标题」栏：目前十几个页面各写一段 `Row { TextButton("返回"); Text(title) }`，下一步收成 `SecondaryTopBar`。
- 「图 + 两行字」的列表行：发现页卡片、主办活动、我的组织、排行榜各一份，等页面结构改成 `LazyColumn` 分块时一起处理。
