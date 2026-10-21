# 官方站点接口实测记录

- 目标站点：`https://win.9xueqi.com`
- 最近验证：2026-10-21（浏览器已登录会话内只读请求，未提交任何写操作）
- 结论：原生首页与活动列表所需接口**全部可用**，参数已锚定

## 基础事实

| 项 | 结论 |
| --- | --- |
| 技术栈 | Vue2 + Vant/VUX 的移动端 H5，hash 路由 |
| 接口前缀 | `https://win.9xueqi.com/api/app/client/v1/` |
| 参数编码 | `params=<JSON>`，最终在线路上是**双重 URL 编码**（代码中只编一次，传输层再编一次） |
| 鉴权 | 请求头 `Authorization: <JWT>`；同时以 query 参数 `token` 携带。无 `Bearer` 前缀 |
| 公共参数 | `platform=3`、`version=2.0.5`、`device`、`timestamp` |
| 会话存储 | 仅 `localStorage.Token`，无 Cookie |
| 验证码 / 签名 | 均未发现 |
| 登录 | `POST /token`，字段 `schoolCode`、`code`、`password`、`openId`，成功返回 `data.access_token` |

## 已验证端点

| 端点 | 参数 | 返回 | 用途 |
| --- | --- | --- | --- |
| `/student/user/my/info` | `{}` | `code:0`，含 `name`、`score`、`credit`、`hours`、`className`、`collegeName`、`majorName`、`grade`、`tabbarModules` | 首页个人信息 |
| `/student/user/my-info` | `{}` | `code:0`，字段与上基本相同 | 我的页 |
| `/message/notice/un-read-count` | `{}` | `{ unReadCount }` | 未读角标 |
| `/activity/processing/statistics` | `{}` | 18 个待办计数（`waitingSignCount`、`activityAuditCount` 等） | 阅办页顶部 |
| `/activity/processing/list` | `{}` | 数组，当前为空 | 阅办列表 |
| `/user/modules` | `{}` | `mine`、`tabBar`、`schoolRestFul`、`stuData`、`stuActivity` | 首页九宫格与底部导航配置 |
| `/dict/activity/choice-sort` | `{"moduleId":"1"}` | `code:0` | 活动排序选项 |
| `/flow-template/list` | `{}` | `code:0` | 申报流程模板 |
| `/login/banner` | `{}` | `code:0` | 登录页轮播 |
| `/page/activity/list` | 见下 | `total`、`pageSize`、`list[]` | 活动列表 |

## `/page/activity/list` 参数（实测抓取）

```json
{
  "search": "",
  "pageNum": 1,
  "pageSize": 10,
  "sortType": 1,
  "activityTime": 1792512000000,
  "classifyId": 0,
  "status": "4,5,6,7",
  "level": 0,
  "organizationName": "",
  "fieldType": "activity_iseErollment",
  "keyWord": "switch"
}
```

要点：

- 共 11 个字段，缺任意一个都可能返回 `10002 参数错误`。
- `activityTime` 是**当天 0 点的毫秒时间戳**，不是字符串日期。
- `fieldType` 的值按其服务端拼写 `activity_iseErollment`（原文即含两个 e），不可“纠正”。
- `status` 为逗号分隔的字符串，不是数组。

## `/page/activity/list` 返回字段（列表项）

`id`、`name`、`logo`、`classifyName`、`classifySecondName`、`hours`、`address`、`organizationName`、
`startTime`、`endTime`、`enrollStartTime`、`enrollEndTime`、`enrollCount`、`peopleLimit`、
`activityStatus`、`isAbleEnroll`、`isfull`、`isRecommend`、`limitType`、`gradeLimit`、`collegeLimit`、`genderLimit`、`honestLimit`。

时间均为毫秒时间戳。`logo` 是阿里云 OSS 绝对地址。

## 未登录时的行为（2026-10-21 实测）

所有业务接口在**不带 token** 时统一返回 `code:10001`「登陆失败，请重新登陆」，包括
`/page/activity/list`、`/student/user/my/info`、`/user/modules`、`/message/notice/un-read-count`、`/dict/activity/choice-sort`。

`/login/banner` 不带 token 可访问，但必须带 `schoolId` 参数，否则返回 `code:10000`。

结论：**没有任何免登录内容**，登录是硬门槛。

## 登录请求格式（来自站内 app.js 的请求拦截器，2026-10-21 核对）

- `Content-Type: application/x-www-form-urlencoded`
- 请求体：`params=<encodeURI(JSON.stringify({schoolCode, code, password, openId}))>`
- 公共参数随请求携带：`token`、`platform=3`、`version=2.0.5`、`device`（固定 UUID）、`timestamp`
- 成功：`code:0`，`data.access_token` 即 JWT
- 无验证码；密码错误等业务错误通过 `code` 返回（如 30005/30006/30007 对应客服与微信绑定提示）

## 找回密码（2026-10-21 实测）

官方页面 `#/resetPassword` 的表单：姓名、学号/工号、手机号、短信验证码、新密码、确认新密码。

| 端点 | 方法 | 参数 | 实测结果 |
| --- | --- | --- | --- |
| `/common/captcha/mobile` | POST | `countryCode:"86"`、`mobile`、`type:2` | 手机号不合法时返回 `10002 手机号格式不正确`，说明端点与参数格式正确 |
| `/common/modify/pwd` | POST | `mobile`、`countryCode:86`、`code`、`zhtxCode`（学号）、`userName`（姓名）、`password`、`passwordRepeat`、`schoolId` | 账号不存在时返回 `30008 账号不存在,请联系管理员或客服中心` |

口令强度规则（取自站内脚本）：至少 8 位，且同时含大写字母、小写字母、数字、特殊字符。

## 学校档案与校徽（2026-10-21 实测）

`GET /common/all-school`（无需登录）返回全部学校，实测 193 条：

```json
{"id": 10361, "name": "安徽理工大学", "firstLetter": "A",
 "logo": "https://gytx-2ketang.oss-cn-hangzhou.aliyuncs.com/logo/aust_logo.png",
 "appWebsiteNavTitle": "...", "systemName": "..."}
```

- 校徽由运营方按学校上传到阿里云 OSS，地址存在学校档案的 `logo` 字段，客户端直接取用。
- **列表顺序由服务端决定**（安徽省内学校在前，其后按平台维护顺序），官方页面原样渲染，客户端不应二次排序。
- **搜索走服务端**：`/common/all-school` 带参数 `{"name":"关键字"}`，为子串匹配；官方页面的搜索框就是这样请求的。
- 部分学校 `logo` 为空，此时只能显示名称。
- 安徽理工的校徽原图仅 60×60，放大显示会发糊；这是平台侧素材问题，不是客户端取图错误。
- `name` 字段可能带首尾换行，需要 trim。

## 活动详情（2026-10-21 实测）

按用户与活动的关系分三个接口，参数都只有一个 `id`（活动 id）。
官方 H5 先调 `GET /activity/member/identity?activityId=<id>` 判定身份（返回 `data.identity`：
1 已参加、2/3 管理员、其它未参加），再决定进哪个详情页；客户端照同样顺序取数。

| 场景 | 接口 | 验证情况 |
| --- | --- | --- |
| 身份判定 | `GET /activity/member/identity` | 已实测（参数是 `activityId`，不是 `id`） |
| 没参加 | `GET /activity/detail/non-member` | 已实测 |
| 已参加 | `GET /activity/detail/participant` | 已实测（活动 574449，identity=1） |
| 管理员 | `GET /activity/detail/manger` | 未实测（服务端拼写就是 `manger`） |

已参加响应里比未参加多的关键字段：

| 字段 | 实测值 | 说明 |
| --- | --- | --- |
| `applyStatus` | `2` | 2 = 报名已通过；其它取值未验证 |
| `status` | `5` | 活动状态（未参加接口叫 `activityStatus`） |
| `isManager` | `0` | 是否管理员 |
| `signInCount` / `signOutCount` | `0` | 签到 / 签退次数 |
| `grantHours` | `0.0` | 已发放学时 |

两个接口共有的字段里，实测确认含义的：

| 字段 | 实测 | 结论 |
| --- | --- | --- |
| `signAudit` | `1` / `0` | 1 需要审核、0 无需审核（与官方页面「报名是否审核」一致） |
| `attendanceType` | `1` | 1 = 签到 + 签退；其它取值未验证 |
| `isSubmitWork` | `1` | 1 需要提交作业 |
| `activityLimitType.type` | `0` | 0 = 个人报名；团队报名取值未验证 |

实测样本（活动 `566506`，电话号码已脱敏，其余原样）：

```json
{
  "id": 566506, "name": "21天单词打卡", "activityStatus": 7,
  "classifyName": "参与团支部层面开展的文体艺术活动",
  "organizationName": "智能采矿25-2", "organizationAvatar": null,
  "logo": "https://.../智育.jpg", "hours": 8.0, "definedHours": 2.0,
  "credit": 98.33, "hoursToCreditRatio": 0.00,
  "startTime": 1791388800000, "endTime": 1793289540000,
  "enrollStartTime": 1790651400000, "enrollEndTime": 1791388740000,
  "address": "百词斩", "contact": "130****3239",
  "peopleLimit": 33, "joinMemberCount": 19,
  "genderLimit": 0, "gradeList": [{"name": "2025级"}],
  "collegeList": [{"name": "矿业工程学院"}], "honestLimit": null,
  "manager": {"name": "刘昊辰", "id": 2197962},
  "activityLimitType": {"type": 0}, "attachment": [],
  "introduce": "为提高大家的学习热情……（纯文本）"
}
```

字段注意点：

- `introduce` **多数是纯文本**，不是 HTML；客户端按是否含标签决定用文本还是富文本渲染。
- `credit` 不可当作「学分」使用：实测 `98.33` 与学时无关，含义未明，客户端不展示。
- `peopleLimit` 为 0 表示不限人数，此时只显示已报名人数。
- `attachment` 是**数组**，空数组即无附件。每项形如
  `{"attachmentType": 5, "name": "通知.pdf", "url": "https://..."}`；
  `attachmentType`：1 图片、3 Word、4 Excel、5 PDF、6 其它。点击直接打开 `url`。
- `gradeList` / `collegeList` 是对象数组，取其中的 `name`。
- 所有时间字段都是毫秒时间戳。
- `honestLimit` 可能为 `null`。

## 活动公告（2026-10-21 实测）

| 场景 | 接口 | 参数 |
| --- | --- | --- |
| 未参加 | `GET /activity/noticeList` | `pageNum`、`pageSize`、`activityId` |
| 已参加 / 管理员 | `GET /activity/noticeListNew` | 同上 |

实测要点：

- 已参加场景下 `noticeListNew` 会返回 `{"total":1,"list":[]}`，**有总数但没有列表**；
  同样的活动用 `noticeList` 就能拿到 `list`。客户端两套都试，取到非空为止。
- 列表元素字段：`id`、`content`（可能是 HTML）、`ctime`（毫秒时间戳）、`isPopup`（1 = 进详情页要弹窗）。
- 没有 `title` 字段，弹窗标题只能用固定文案。

写操作的接口清单（仅记录，未接入）：`activity/enroll/person`、`activity/enroll/team`、
`activity/enroll/cancel/person`、`activity/enroll/leave/person`、`activity/sign/repair`、
`activity/sign/switch`、`activity/submit/homework/list`。

## 只读查询（已实测）

| 接口 | 参数 | 实测返回 |
| --- | --- | --- |
| `GET /activity/sign/count` | `id` | `{"signInCount":0,"signOutCount":0,"memberCount":30,"activityName":"..."}` |
| `GET /activity/submit/homework/list` | `pageNum`、`pageSize` | `{"total":0,"pageSize":20,"list":[],"pageNum":1}` |

## 写操作封装（已就绪，未接线）

`data/ActivityActions.kt` 里封好了写接口，参数取自官方 H5 源码，**没有任何界面入口，
也没有任何地方会自动调用**；一旦被调用，会把请求参数与原始响应写进日志（`[action]` 通道）。

| 方法 | 接口 | 参数（H5 源码） |
| --- | --- | --- |
| 个人报名 | `POST activity/enroll/person` | `id`、`personMaterial?` |
| 团队报名 | `POST activity/enroll/team` | `id`、`teamName`、`teamAvatar`、`teamMembers`、`teamMaterial?` |
| 取消报名 | `POST activity/enroll/cancel/person` | `id` |
| 补签 | `POST activity/sign/repair` | `activityId`、`userIds` |
| 作业列表 | `GET activity/submit/homework/list` | `pageNum`、`pageSize` |
| 签到人数 | `GET activity/sign/count` | `id` |

调试只读接口（不产生任何写入）：

```
adb shell am start -n gitee.leolin_me.erkepp.debug/gitee.leolin_me.erkepp.MainActivity \
  --es probe homework_list
adb shell am start -n gitee.leolin_me.erkepp.debug/gitee.leolin_me.erkepp.MainActivity \
  --es probe sign_count --el detail_id 574449
```

结果看 `adb shell run-as gitee.leolin_me.erkepp.debug cat files/logs/erkepp.log` 里的 `[action]` 行。

## 作业（2026-10-21 实测）

| 场景 | 路由 | 接口 |
| --- | --- | --- |
| 查看作业 | `#/assignmentinfor/:id` | `GET activity/query/work_3_0_1?activityId=` |
| 交作业 / 修改作业 | `#/assignment/:id` | 写操作，未接入 |

注意路由名和路径不是一回事：路由 name 叫 `partyAssignment` / `partyAssignmentinfor`，
但路径是 `/assignment/:id` 与 `/assignmentinfor/:id`；组内还有另一套 `/partyAssignment*` 是党团活动用的。

实测返回（活动 570315）：

```json
{"code":0,"data":{
  "activityId":570315,
  "introduce":"讲座让我深刻认识到……",
  "attachment":[{"size":1175704,"name":"deepseek_xxx.png","id":8267529,"type":1,"url":"https://..."}],
  "attachUrls":"https://...png,https://...png"
}}
```

- 正文在 `introduce`，不是 `content`。
- 作业附件只有 `type` 字段（活动详情的附件用 `attachmentType`），两者都要兼容。
- 作业入口在官方只在这些情况下出现：活动已开始 / 已结束，或本人已提交过作业。

## 尚未验证

- 报名、取消、签到等**写操作**（未验证，禁止先行接入）
- 已参加（`detail/participant`）与管理员（`detail/manger`）两个详情接口的实际响应
- 二课成绩页（`#/scoreReport`）实际接口
- 学生组织、问卷、打卡等长尾页面
