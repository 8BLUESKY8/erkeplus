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

## 报名与取消报名（来自 H5 源码，v0.7.1 接入原生）

```
POST /activity/enroll/person        { id, personMaterial: [] }      // 个人报名
POST /activity/enroll/cancel/person { id, applyInfo }               // 取消报名
```

- `personMaterial` 是报名材料数组；活动没有材料时官方也传空数组。
  `enrollMaterial=1` 的活动需要先填材料，App 端不接，引导去官方页面。
- 取消报名：**距活动开始不足 1 小时不允许**（H5 直接弹提示）。
- `applyInfo` 是取消理由：`applyStatus=2`（报名已通过）时**必填**，提交后变为「取消审核」；
  未通过时官方也带这个字段（空串），成功后名额立即释放。
- 成功后 H5 会重新拉一次详情；取消成功的文案区分「已成功取消报名」和
  「报名取消申请已提交，等待审核」。

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
- **`search` 只作用于 `total`，不作用于 `list`**（2026-10-22 实测）：传 `search=挑战杯` 时
  `total=5` 但 `list` 仍是未过滤的第 1 页（「21天单词打卡」之类）。因此客户端不该依赖它做搜索，
  始终传空串；搜索改成本地匹配（官方 H5 的 `getSearc` 也是 `name.indexOf` 前端过滤）。

## 状态编号与列表可见性（2026-10-23 实测）

`activityStatus` 是一个数字，学生端能看到的只有一部分。判断「能不能报名」不能只看时间，
还要看这个数字：**0 是草稿、1 和 3 是未审核**。

| status | 学生端看到的样子 | 样本 |
| --- | --- | --- |
| 0 | 草稿 / 未发布，没有报名入口，学时未结算 | 576539 21天单词打卡 |
| 1 | 未审核，0 学时，报不了 | 572514 武术与民族传统武术展演 |
| 3 | 未审核，0 学时，报不了 | 576140 21天单词打卡 |
| 4 | 待报名 | 576596 2026级外国语新生迎新破冰活动 |
| 5 | 报名中（唯一可报名的编号） | 566950 毕业生德育铸魂引领活动 |
| 6 | 报名已截止、活动未开始 | 575705 |
| 7 | 进行中 | 574792 |
| 8 | 已结束 | 569941 |

`status=0` 是 2026-10-24 补测的：`page/activity/list` 传 `status=0` 返回 **27 条**、条目
`activityStatus` 全是 0（21天单词打卡、单词21天打卡、七天单词打卡……），都有名字和时间，
但没有报名入口。注意它**不是**「全部」——H5 里的 `status:0` 只是「全部」这个 tab 的下标，
真正发出去的请求写死 `status:"4,5,6,7"`（连已结束的 8 都不带）。

「未审核」不是我猜的：打开 `#/activityshow/572514` 与 `#/activityshow/576140`，页面底部
按钮文案就是「未审核」（`.EventDetailshow-bottom-all`），顶部学时写「（0学时）」。

同一时刻用 `page/activity/list` 逐个数 `total`：

| status | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9~12 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 条数 | 2 | 0 | 44 | 0 | 82 | 3 | 89 | 4 | 0 |

半小时后重数是 5→81、6→4、7→88、8→5（活动在途会换编号），学生端可见合计 178 条。
按 `4,5,6,7,8` 拉全量复核：178 条里 `hours <= 0` 的有 **0** 条；未审核那 46 条则普遍是 0 学时。

要点：

- **未审核（1、3）只在显式按状态过滤时返回**：`status=1` 给 2 条、`status=3` 给 44 条。
  官方「全部活动」页（`activity/list`，我校 82 条）不含它们，学生端也不给报名入口。
  客户端问它们要活动，看着像「漏了一批正在报名」的活动，其实那批根本没通过审核。
- **`status=4` 恒为 0**：待报名的活动被服务端从所有列表里摘掉，拼任何 `status` 都查不到。
- `activity/list`（网页「全部活动」页）与 `activity/search/all/list`（`searchName=破冰`）都不返回状态 4 的活动。
- 详情接口 `activity/detail/non-member?id=576596` 正常返回，**不校验报名是否开始**。
- 活动 id 自增但不密集：576580~576615 共 36 个 id 里只有 576596 一条是真活动，其余返回 `10000`。
  所以「按 id 扫尾部」可以在报名开放前发现新活动；这是第三方推送机器人能提前拿到活动的最合理解释。

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

## 诚信值（2026-10-23 实测）

官方页面 `#/honestValue`，三个只读接口：

| 端点 | 参数 | 返回 |
| --- | --- | --- |
| `GET credit/user/detail` | `{}` | `{"faithUpdateTime":1792227641000,"blacklist":null,"credit":98.33}` |
| `GET dict/school/system-para/credit/list` | `{}` | 分数段数组，见下 |
| `GET credit/user/list` | `searchName`、`type`、`pageNum`、`pageSize` | `{total, list}` |

分数段（我校）：

```json
[
  {"minScore": 100.0, "maxScore": 100.0, "grade": "完美"},
  {"minScore": 90.0,  "maxScore": 99.0,  "grade": "优异"},
  {"minScore": 80.0,  "maxScore": 89.0,  "grade": "良好"},
  {"minScore": 70.0,  "maxScore": 79.0,  "grade": "中等"},
  {"minScore": 60.0,  "maxScore": 69.0,  "grade": "一般"},
  {"minScore": 0.0,   "maxScore": 59.0,  "grade": "较差"}
]
```

要点：

- **分数段给的是小数**（`100.0`），`intOrNull` 读出来是 null，直接用会把 100 读成 0。
  客户端统一按 double 读再截断。
- `type`：`1` 待守约、`2` 已守约、`3` 已失约（H5 传的是 `chooseValue - 1`，标签 2/3/4 对应
  chooseValue）。**申诉记录不走这个接口**，它是独立的 `GET credit/user/appeal-list`
  （参数 `{searchName, pageNum, pageSize}`）。`credit/user/list` 传 `0`、`3`、`4` 都返回失约记录，
  拿 `type=4` 当申诉列表会把「已失约」和「申诉记录」混在一起。
- 申诉条目的 `appealStatus`：`1` 申诉待审核、`3` 申诉被驳回、其它算已通过。
- 待守约的条目只有活动的字段（`relationName`、`logo`、`startTime`、`endTime`、`status`）；
  已守约 / 已失约多出 `amount`、`operator`（`+`/`-`）、`attendanceType`、`ctime`、`comment`。
  实测已守约的 `amount` 是 0，失约是扣分（1.67）。
- 页面上的「信用优异」= `credit` 落在哪个分数段，不是单独字段。

## 个人资料字段（2026-10-23 实测）
`GET student/user/my-info` 里有两个容易搞混的数字：

| 字段 | 含义 | 实测值 |
| --- | --- | --- |
| `credit` | **诚信值**（0~100），与 `credit/user/detail` 的 `credit` 一致 | 98.33 |
| `score` | **学分**，与成绩单 `#/scoreReport` 一致 | 0 |
| `hours` | 学时 | 0 |

客户端曾把 `credit` 当学分显示（「我的」页 98.3 学分），成绩单却是 0，就是这个原因。

头像与其他资料：

- `avatar` 是头像地址，`cardPhoto` 是成绩单上的证件照，两者都在阿里云 OSS。
- **头像不限文件类型**：实测用户上传的 GIF 被原样保存，但文件名改成 `.jpg`、
  服务端返回 `Content-Type: image/jpeg`，而文件头仍是 `GIF89a`。
  按 MIME 判断的解码器（Coil）会把它当静态图，只画第一帧；客户端因此改为自己取字节、
  按文件头调用 `ImageDecoder`，才能正常播放动图。

## 个人信息接口（2026-10-23 从 H5 源码核对）

读：

| 端点 | 参数 | 说明 |
| --- | --- | --- |
| `GET student/user/my/info/mine` | `{}` | 详细资料，比 `my-info` 多 `mobile`、`ethnic`、`ethnicId`、`politics`、`politicsName`、`personTag` |
| `GET student/user/my-person-info` | `{}` | 「我的信息」页用的资料，字段与上面基本一致 |
| `GET student/get/nation_list` | `{}` | 民族数组，提交时用「下标 + 1」 |
| `GET dict/user/tag` | `{}` | 标签字典 `[{id, name}]` |

写（H5 源码里的调用，**未实测**）：

| 端点 | 参数 | 说明 |
| --- | --- | --- |
| `POST student/user/update-info` | `{gender}` / `{politics}` / `{ethnicId}` / `{avatar}` | 一次只提交一个字段 |
| `POST student/user/update-mobile` | `{oldMobile, countryCode:86, code, newMobile}` | 验证码经 `POST common/captcha/mobile {mobile:<旧号>, type:2}` 发到旧号 |
| `POST student/user/update-pwd` | `{oldPwd, newPwd, newPwdRepeat}` | 成功后 token 失效，官方清 `TOKEN` 回登录页 |
| `POST student/user/update-person-tag` | `{personTag:"8,9,5"}` | 逗号分隔的标签 id |
| `POST student/photo/update` | `{cardPhoto} ` | 成绩单证件照；头像不走这里 |

`politics` 取值：`0` 群众、`1` 共青团员、`2` 中共党员、`3` 其他党派、`4` 中共预备党员。

## 成绩单与排行榜（2026-10-24 实测）

| 端点 | 参数 | 返回 |
| --- | --- | --- |
| `GET student/achievement/rank` | `pageNum`、`pageSize`、`level` | `{total, pageSize, pageNum, list[{rownum, hours, score, name, code, className, majorName, collegeName, grade, campusName, avatar, gender}]}` |
| `GET student/achievement/self/rank` | `level` | 本人的单条记录，字段同上 |

`level` 的取值来自官方 H5（`reportRank` 页）：**6 全校、8 院系、9 专业、10 班级**，
进去默认看班级，右上角下拉切换。官方 `pageSize` 固定 10，本项目用 20。

**排行榜里的「成绩」就是学分**，不是学时也不是诚信值：

```
score = hours × 0.025        // 我校 40 学时 = 1 学分
169h → 4.225   148h → 3.7   143h → 3.575   93h → 2.325   69h → 1.725
7h → 0.175     3h → 0.075   2h → 0.05      1h → 0.025     0h → 0
```

全校 39 条与班级 39 条都严格满足这个比例，且与 `my-info.score`、成绩单页一致
（我的页「学分」显示的也是它）。规则来自学校的学时折算学分配置（`ways-convert` 返回
`waysConvert=1`，即启用折算），客户端只展示服务端算好的 `score`，不要自己乘。

字段上的坑（都是实测）：

- `rownum` 是名次，**并列同名次**（例如 2、2、3、3、3、4）；**也可能是空串**：
  一次都没上榜的同学就是 `""`，解析时按 0 处理，界面显示「—」。
- `score` 与 `hours` 都是 double（`0.175`、`7.0`），按 int 读会拿到 0。
- `grade` 是数字 `2026`，不是「2026级」。
- `avatar` 是完整 URL，可能是空串。
- **响应里没有 `lastPage`**：`data` 只有 `total` / `pageSize` / `pageNum` / `list`。
  官方 H5 读的 `a.lastPage` 永远是 `undefined`，实际靠 `list.length === total` 收尾。
  本项目按 `pageNum × pageSize >= total` 判断是否到底（班级 39 人两页、全校 100 人五页都准）。
- **全校与院系只给前 100 条**：`total` 就是 100，第 6 页返回空数组。这是服务端上限，
  官方页面同样只翻到 100 条，不是客户端翻页写错。
- 名次完全由服务端算：0 学时的我在班级里并列第 4，在全校园里报第 58，客户端照显示。

样本（`level=10&pageNum=1&pageSize=2`，2026-10-24）：

```json
{"code":0,"data":{"total":39,"pageSize":2,"pageNum":1,"list":[
  {"hours":7.0,"code":"2026306614","gender":1,"campusName":"淮南校区","className":"信计26-3",
   "avatar":"https://gytx-2ketang.oss-cn-hangzhou.aliyuncs.com/...jpg","rownum":1,"score":0.175,
   "collegeName":"数学与大数据学院","grade":2026,"name":"王俊文","id":2497513,
   "majorName":"信息与计算科学"}
]},"msg":"ok"}
```

## 组织（2026-10-24 实测）

官方有三处入口都指向同一套接口：`#/myOrganization`（我的组织）、`#/service/organize`
（校园组织目录）、`#/orgDetail/<id>`（组织详情）。第三个是扫组织码的落点。

| 场景 | 接口 | 参数 |
| --- | --- | --- |
| 我加入的 | `GET organization/my-list` | `pageNum`、`pageSize`、`status` |
| 我创建的 | `GET organization/my-create-list` | 同上 |
| 我退出的 | `GET organization/my-quit-list` | 同上 |
| 组织目录 | `GET organization/all-list` | `pageNum`、`pageSize`、`type` |
| 组织详情 | `GET organization/detail` | `id` |
| 组织活动 | `GET organization/activity-list` | `organizationId`、`pageNum`、`pageSize` |
| 组织成员 | `GET organization/member-list` | `organizationId`、`searchKey`、`userLastElectId` |
| 申请加入 | `POST organization/user-apply/add` | `id`、`applyInfo` |

- **`status` 是加入申请的审核状态**：1 待审核、2 已通过、3 已驳回（H5 里发的是
  `status: activeStatus + 1`）。服务端按它过滤，三个接口都要带。
- **`type` 传 1 和 2 返回同一批数据**（都是 131 个，逐项比对过），所以固定传 1。
- 分页响应 `data` 里有 `total / pageSize / pageNum / list`，**没有 `lastPage`**。
- 列表项里 `orgProcessStatus` 是**字符串码**，不是数字：`re1` 待注册、`re2` 注册中、
  `re3` 已注册、`ap2` 评优中、`ya1` 待年审、`ya2` 年审中、`ya3` 整改中、`ct1` 换届中、
  `cw1` 待注销、`cw2` 注销中、`cw3` 已注销。未知码不要自己编文案。
- `identity` 是「我在这个组织里的身份」：3 管理员、2 负责人、其它按成员。目录里 0 表示没加入。
- `joinTime` / `ctime` 是毫秒时间戳；列表上官方写成「YYYY.MM.DD HH:mm 申请」。
- `introduce` 在列表里是一句话简介，很多组织直接填了和名称一样的字。

样本（`my-list?pageNum=1&pageSize=3&status=1`，2026-10-24）：

```json
{"code":0,"data":{"total":2,"pageSize":3,"pageNum":1,"list":[
  {"joinTime":1792804049000,"introduce":"化学与火药纹画协会","chairmaId":1555524,
   "memberCount":119,"orgProcessStatus":"re3","auditRemark":"","avatar":"",
   "chatGroupId":"O10361_17054","name":"化学与火药纹画社","id":17054,
   "currentFlowCategory":"organ-3","energy":663.0,"status":1}
]},"msg":"ok"}
```

详情（`detail?id=39261`）多给这些字段：`nature`（数字）、`natureName`、`categoryName`、
`affiliationName`、`chairmanName`、`guiderName`、`campusName`、`level`、`rule`、
`isJoin`、`isAdd`、`applyStatus`、`identity`、`isChairman`、`electApplyStatus`、
`announcementList`、`flowExampleAuditRecordList`，以及**整个成员列表** `memberList`
（几百条，实测 793 条、1052 条，详情一次全给，别在 UI 里当列表用）。

详情页真正用得上的字段（2026-10-24 实测，机器人协会 17085 / 人工智能协会 39261）：

| 字段 | 实测 | 说明 |
| --- | --- | --- |
| `activityCount` | 13 / 8 | 官方「活动数」；**和 `activity-list` 的 `total` 对不上**（13 vs 9、8 vs 5），同一页只能挑一个用 |
| `foundTime` | 1659379982000 | 成立时间，毫秒时间戳 |
| `campusName` | 淮南校区 | |
| `parentName` | 空串 | 官方「组织归属」用它，空就显示「暂无」 |
| `affiliationName` | 安徽理工大学 | 挂靠单位 |
| `announcementList` | `[]` | 公告；实测这两个组织都没有，非空时的元素结构未验证 |
| `memberList` | 1052 条 | 精简成员：`{"userLastElectId":685,"name":"郑爽","id":1321746,"avatar":null}`，没有职务 |
| `joinRuleType` | 1 / 2 | 含义未验证，客户端不展示 |
| `identity` | 1 | 我在这个组织里的身份，口径与列表一致 |

`memberCount` 在三处接口里数值不一样（17085：detail 1052、`all-list` 3354、`my-apply-list1` 874），
哪个页面就用哪个接口给的值，客户端不做「校正」。

### 组织成员（`member-list`，2026-10-24 实测）

参数 `organizationId`、`searchKey`（搜索框，空串取全部）、`userLastElectId`（「届」的 id，
官方从详情里取；实测传空串也能拿到当前这一届）。响应：

```json
{"code":0,"data":{
  "managerList":[{"organizationName":"人工智能协会","nature":2,"avatar":"https://...",
    "recordId":1794697,"collegeName":"人工智能学院","electId":553,"identity":1,
    "name":"李梦臣","duty":"会长","applyPosition":1,"id":1376788,"majorName":"机器人工程",
    "department":""}],
  "waitAuditMembers":113,"chairman":null,
  "commonList":[{"name":"马志强","recordId":1794705,"collegeName":"人工智能学院",
    "electId":553,"identity":1,"duty":"","applyPosition":null,"id":1682122,"majorName":"人工智能"}]}}
```

- `managerList` 是干部，带 `duty`（会长 / 副会长 / 部长）；**顺序不是按职务排的**——机器人协会
  的会长排在三个副会长后面，客户端要自己排，否则页面上像「没有会长」。
- `commonList` 是普通成员（实测 869 / 437 条），官方成员页在里面搜索、翻页。
- `chairman` 实测为 `null`；`waitAuditMembers` 是待审核入团申请数。
- 官方进成员页前会拦「您不是该组织成员」（看详情的 `identity`），**服务端不拦**：
  非成员同样拿得到完整名单，拦不拦是产品决定。

### 组织相册 / 撤销申请状态

| 用途 | 接口 | 参数 | 实测返回 |
| --- | --- | --- | --- |
| 相册 | `GET organization/photo/list` | `id` | 两个组织都是 `data: []`；H5 按分组渲染，每组 `photos:[{url,id}]` |
| 能否撤销申请 | `GET organization/query/count` | 无 | `{"revoke":0}`，1 表示可以撤销 |

**申请加入的显示条件**（照官方 `orgDetail` 页，再按实测补一条）：

- 官方：`(isJoin == 0 && isElect == 0 || applyStatus == 3) && rule != 3 && isAdd == 1`
- `isElect` 只在扫码进来的那条路径上有值（`query.isElect=0`），直接进详情时是 `null`，
  按官方写法按钮反而出不来。本项目改成按 `applyStatus` 判断：
  `applyStatus ∈ {0, 3} && rule != 3 && isAdd == 1` —— 没申请过、或曾被驳回时才给按钮。
- `applyStatus`：0 没申请、1 待审核、2 通过、3 驳回。实测申请后 `isJoin` 也变 1，
  所以不能只看 `isJoin`，否则会对同一组织重复提交。

**组织码**：官方生成 `qutuo://joinOrganization?id=<组织id>`，经 `Encrypt` 处理再补
`?yiban=yiban_scan_result`。包里的 `Encrypt` / `Decrypt`（模块 `aNLv`）都是
`function(t){return t}`，恒等函数——所以二维码里就是明文，客户端不需要解密。

真机样本：`qutuo://joinOrganization?id=39261?yiban=yiban_scan_result`（注意第二个问号）。

### 组织目录（`#/service/organize`，2026-10-24 实测）

官方那一页用的**不是** `organization/all-list`，而是这一对：

| 用途 | 接口 | 参数 |
| --- | --- | --- |
| 分类字典 | `GET dict/organization/choice-sort` | 无 |
| 目录列表 | `GET organization/page/organization/list` | `search`、`nature`、`sortType`、`pageNum`、`pageSize` |

分类字典返回 `data.filterArray`（`[{id,name}]`）和 `data.sortArray`。实测：

```json
{"code":0,"data":{
  "sortArray":[{"id":"1","name":"最热"},{"id":"2","name":"最活跃"}],
  "filterArray":[{"name":"全部","id":0},{"name":"学生会组织","id":3},
                 {"name":"社团与协会","id":2},{"name":"志愿组织","id":5},{"name":"其他","id":19}]
},"msg":"ok"}
```

- `nature` 用 `filterArray` 的 `id`，0 = 全部。实测总数：全部 140、社团与协会 99、
  学生会组织 37、志愿组织 0、其他 1。
- **`sortType` 是 0 / 1，不是 1 / 2**：官方页面的下拉写死「最新 / 最热」，但 `popClick`
  里 最新 → `sortType=0`、最热 → `sortType=1`。字典里那个 `sortArray`（1 最热、2 最活跃）
  是别的页面用的，照它发会把排序弄错。
- **`search` 服务端不认**：传「协会」「人工智能」和传空字符串返回的总数都是 140，
  列表也不变。官方那个搜索框实际上筛不出东西，客户端要搜只能自己拉全量再匹配。
- 目录条目字段：`id`、`name`、`avatar`、`introduce`、`memberCount`、`energy`、
  `identity`、`rule`、`isAdd`、`isElect`。其中 **`isElect` 实测 100 条全是 1**、
  `isAdd` 全是 1，所以官方模板里「isElect==1 → 换届中」在目录里等于给每条都挂同一块牌子，
  客户端不拿它当「正在换届」用。
- 申请条件：`rule != 3`（3 = 不接受自己申请）且 `isAdd == 1`。

样本（`nature=0&sortType=0&pageNum=1&pageSize=1`）：

```json
{"code":0,"data":{"total":140,"pageSize":1,"pageNum":1,"list":[
  {"chatGroupId":"O10361_17072","introduce":"青年志愿者协会","identity":0,
   "chairmaId":1555530,"memberCount":0,"name":"青年志愿者协会","rule":3,
   "id":17072,"avatar":"","isAdd":1,"energy":4800.0,"isElect":1}
]},"msg":"ok"}
```

`memberCount` 在目录里经常是 0（和详情、`all-list` 对不上），显示时不要当权威。

### 目录页另外两个 tab（2026-10-24 实测）

官方 `#/service/organize` 顶部（下拉）的三个 tab 对应三个接口，参数都只有
`search`、`nature`、`pageNum`、`pageSize`：

| tab | 接口 |
| --- | --- |
| 未申请 | `organization/page/organization/list`（就是上一节那个） |
| 已申请 | `organization/my-apply-list1`（**结尾带 1**） |
| 已加入 | `organization/my-list`（按分类，不带 `status`） |

`my-apply-list1` 的字段名和别处都不一样：

```json
{"code":0,"data":{"total":2,"pageSize":3,"pageNum":1,"list":[
  {"organizationId":39261,"organizationName":"人工智能协会","memberCount":441,
   "organizationAvatar":"","applyRefuseReason":"","id":1072528,
   "applyTime":1792803677000,"applyStatus":1,"energy":925.0}
]},"msg":"ok"}
```

- 组织 id 是 `organizationId`；`id` 是这条申请记录自己的 id，别拿混。
- `applyStatus`：1 待审核、2 已通过、3 已驳回（驳回理由在 `applyRefuseReason`）。
- 通过之后这条记录仍留在「已申请」里，同时组织出现在「已加入」。实测：
  化学与火药纹画社 `applyStatus=2` → 两个 tab 都有它；人工智能协会 `applyStatus=1` →
  只在「已申请」。

## 作业（2026-10-21 实测）

| 场景 | 路由 | 接口 |
| --- | --- | --- |
| 查看作业 | `#/assignmentinfor/:id` | `GET activity/query/work_3_0_1?activityId=` |
| 交作业 / 修改作业 | `#/assignment/:id` | `POST activity/submit/work_3_0_1`，参数见下节 |

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

## 作业提交（2026-10-22 挖参数，未实测）

`POST activity/submit/work_3_0_1`，body：

```json
{"activityId": 570315, "introduce": "作业正文", "attachment": [
  {"id": 0, "url": "https://...", "name": "a.png", "size": 400214, "type": 1}
]}
```

- 正文不能为空（官方前端会提示「请填写作业」）。
- 附件最多 9 个，单个不超过 5 MB。新增附件的上传链路（2026-10-24 打通）：

  ```
  GET  common/aliyun/sts/assume-role
       → data{accessKeyId, accessKeySecret, accessSecurityToken}
  PUT  https://gytx-2ketang.oss-cn-hangzhou.aliyuncs.com/activity/<毫秒时间戳>/<文件名>
       Authorization: OSS <accessKeyId>:<Base64(HMAC-SHA1(secret, StringToSign))>
       x-oss-security-token: <accessSecurityToken>
  ```

  图片走 `gytx-2ketang`，其它文件走 `gytx-temp`；对象路径照 H5 的 `activity/<毫秒>/<文件名>`。
  签名是 OSS **V1**（H5 的 ali-oss 默认也是 V1），字符串为
  `PUT\n<Content-MD5>\n<Content-Type>\n<Date>\nx-oss-security-token:<token>\n/<bucket>/<key>`。
  `attachment` 里 `url` 可有多个（H5 传原图 + 压缩图，逗号分隔），取第一个即可。
- 客户端提交前有二次确认，请求与响应写 `[action]` 日志。

## 人物主页（2026-10-25 实测）

官方 `#/userShows/<id>` 只调一个接口，外加关注/取关两个写接口：

| 用途 | 接口 | 参数 | 实测 |
| --- | --- | --- | --- |
| 主页数据 | `GET message/chat/follow/user/info` | `id`（用户 id） | 已实测（样本 1908337） |
| 关注 | `POST message/chat/follow/add` | `id` | 已接，未实测返回 |
| 取关 | `POST message/chat/follow/delete` | `id` | 同上 |

响应样本（头像 URL 已缩短，其余原样）：

```json
{"code":0,"data":{
  "politics":0,"politicsName":"","hours":273.5,"gender":1,
  "organizationName":"","avatar":"https://.../1784864580174.jpeg",
  "dynamicCount":0,"organizationCount":3,"activityCount":32,
  "collegeName":"化工与爆破学院","isFollow":0,"identity":1,"schoolId":10361,
  "name":"程子涛","id":1908337,"majorName":"弹药工程与爆炸技术",
  "personTag":[{"name":"电竞·游戏","id":2},{"name":"电影","id":3}]}}
```

- `identity`：1 学生（页面显示「学院 - 专业」）、2 组织账号（显示「学院 - 组织名」）
- `gender`：1 男、2 女，其它表示没填
- `isFollow`：1 已关注、0 未关注；`activityCount` / `organizationCount` / `dynamicCount` 是页面上的三个计数
- `personTag` 是对象数组，取 `name`
- `hours` 是**学时**，官方页面没摆出来，客户端也不摆（见 `docs/design/person.md`）
- 组织账号的 `identity=2` 只在模板里出现，实测样本是学生，未验证

## 尚未验证

- 报名、取消、签到等**写操作**（未验证，禁止先行接入）
- 已参加（`detail/participant`）与管理员（`detail/manger`）两个详情接口的实际响应
- 二课成绩页（`#/scoreReport`）实际接口
- 学生组织、问卷、打卡等长尾页面
