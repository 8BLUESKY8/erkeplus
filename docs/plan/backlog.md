# 待办

只放**还没做**的事，按用户提出的顺序记录，不排优先级；动手前先在这里把规则写清楚。
做完的条目移进文末「已完成」，只留一行结论与落点，细节交给 `CHANGELOG.md` 和对应文档。

## 1. 组织成员列表页

**现状**：组织详情只显示成员数，点不进去。数据层其实已经齐了——`organization/member-list`
早就在取（详情页用它渲染「负责人」），`OrgMembers` 里 `chairman` / `managers` / `common` /
`waitAudit` 都在，`OrgMember` 带 `duty`（会长 / 副会长 / 部长…）、`collegeName`、`majorName`。
缺的只是页面。

**要做**：

- 入口：组织详情的成员数 / 「负责人」区块，点进去是本组织的成员列表
- 分组照官方：负责人 → 干部（`managers`，按职务高低排）→ 成员（`common`），每行头像、姓名、
  学院班级、职务；`waitAudit > 0` 时提示还有几份待审核申请（详情页不显示这个数，留给这里）
- 每行接人物主页，用 `OrgMember.id`，`id <= 0` 保持不可点（同排行榜的做法）
- 搜索：接口给了 `searchKey`，先实测它在服务端是否真的过滤；确认之前不要假装搜过
  （拿到的是全量就如实说明，别让用户以为过滤生效了）
- 官方在点进成员页时拦「您不是该组织成员」，服务端并不拦。**非成员也能看名单**，
  这个差异要在真机上确认后写进 [docs/api](../api/win-9xueqi-api.md)，别把官方的拦截照搬成客户端的拦截

**状态**：未开始。

## 已完成

- 2026-10-25 **已报名的活动不被屏蔽词吃掉**：`BlockRules.blocks(item, blockEnrolledToo)`，
  报名状态取 `applyStatus`（1 待审核 / 2 已通过），开关在「设置 → 内容屏蔽」，默认关闭。
- 2026-10-25 **排行榜与组织负责人可点进人物主页**：`RankEntry.userId`、`OrgMember.id`，
  `id <= 0` 的行不可点；组织成员列表做出来后同样接这里。
- 2026-10-25 **人物主页**：`UserProfileScreen`，接口 `message/chat/follow/user/info`，
  字段说明见 [docs/design/person.md](../design/person.md)。
