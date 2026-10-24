# 消息通知（规划，未实现）

官方 `#/needDealt` 是个纯站内列表，只有打开网页才看得到。目标是把里面的消息变成**系统通知**：
有新通知就弹，点开直接落在对应的活动/组织页；不在应用里也能收到。

状态：接口已实测（2026-10-24），界面和后台轮询都还没做。

## 接口（实测）

| 用途 | 接口 | 参数 | 实测返回 |
| --- | --- | --- | --- |
| 未读数 | `GET message/notice/un-read-count` | 无 | `{"unReadCount":1}` |
| 列表 | `GET message/notice/list` | `pageNum`、`pageSize` | `{"total":11,"pageSize":10,"list":[...]}` |
| 置已读 | `POST message/notice/read` | `id`（从 H5 源码看，未实测） | 未测 |
| 全部已读 | `POST message/notice/read-all` | 无 | 未测 |
| 清理 | `POST message/notice/clean` | 未测 | 未测 |
| 删除单条 | `POST message/delete` | `id`（H5 `129.js`） | 未测 |

列表项字段（真实样本）：

```json
{"id":99667222,"type":3,"typeName":"组织通知","subType":13,"subTypeName":"成功加入部落",
 "content":"恭喜你成功加入机器人协会组织","isRead":0,"ctime":1792834665000,
 "icon":"","showDelete":"false","extras":{"jumpInfo":"17085"}}
```

- `type`：2 活动通知、3 组织通知（`typeName` 直接可用，不要自己写死文案）
- `subType`：实测 6 学时已发放、12 开始签到签退、13 成功加入组织、17 个人报名审核通过…；
  `subTypeName` 是现成的短标题，直接当通知标题用
- `content`：一句话正文
- `extras.jumpInfo`：落点 id。`type=3` 是组织 id，`type=2` 是活动 id；`extras.managerId` 是操作人
- `isRead`：0 未读、1 已读；`ctime` 毫秒时间戳

## 设计

**数据层**：新增 `NotifyRepository`（list / unreadCount / markRead / markAllRead），
外加一份本地快照（DataStore）：已知最大消息 id、上次拉取时间、已弹出过的 id 集合。
快照的作用是**去重**——同一条消息只弹一次，重装后也不会把历史一次性刷出来。

**轮询**：`WorkManager` 的 `PeriodicWorkRequest`（系统下限 15 分钟），间隔可选
15 / 30 / 60 分钟或关闭，默认 30 分钟；另外每次冷启动、每次切回前台各拉一次。
Doze 下系统会把任务推后，所以文案只说「定期检查」，不承诺实时。

**通知**：一个渠道「通知」，重要性默认 `DEFAULT`（用户可调到横幅）。
标题用 `subTypeName`，正文用 `content`，按 `type` 加不同的图标/分组；
`isRead == 1` 的不弹。多条同时到达时先发一条摘要（「3 条新通知」），点开进列表页。

**点击跳转**：走现有的覆盖层栈——
`type=2` → 活动详情；`type=3` → 组织详情；认不出的落点回退到官方页面。
点开并把消息置为已读（`message/notice/read`），与官方「打开就变已读」一致。

**权限**：Android 13+ 需要 `POST_NOTIFICATIONS`，在用户第一次打开「通知」开关时申请；
被拒绝时开关置灰并说明去系统设置打开，不做二次骚扰。

**边界**：退出登录、切换学校、切换账号都要清空本地快照并取消已排队的通知；
服务端 `unReadCount` 与本地弹过的条数不一致时以服务端为准，不要自己回算。

## 里程碑

1. **M1 只读列表页**：照 `#/needDealt` 做一页（分页、标记已读、全部已读、未读角标），
   「更多」里加入口。这一步不涉及权限和后台，先把数据跑通
2. **M2 系统通知**：WorkManager 轮询 + 通知渠道 + 去重 + 点击跳转
3. **M3 设置**：间隔/开关、免打扰时段
4. **M4 打磨**：摘要分组、通知里直接「去签到」

## 待验证

- `message/notice/read` 的参数名（`id` 还是 `noticeId`）和返回结构
- `read-all` / `clean` / `message/delete` 的参数与副作用
- 各 `subType` 的 `jumpInfo` 落点是否统一（现在只有 2/3 两类样本）
- `unReadCount` 是否包含聊天类消息（`/message/chat/*`），会不会和官方的红点数对不上
