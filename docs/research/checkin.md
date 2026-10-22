# 签到签退：官方实现与可行方案（研究记录）

> 2026-10-22。依据：线上 H5 bundle（与本地 `tem/h5` 逐字节同版，hash 一致）+ 真机只读接口实测
> + 旧项目 `aust_secondclass` 的逆向记录。**本文不含任何写操作实测结论。**

## 0. 一句话结论

扫码签到**不需要微信**：现场二维码是**明文**字符串，官方 H5 自己也不拍照，它把扫码交给宿主 App
（微信/企业微信/钉钉/易班）的 bridge，拿回文本后解析、直接 POST 一个普通接口。所以安卓端只要
自己拍照解出那段文本，就能走完全相同的路径——接口契约与官方逐字段一致。

## 1. 为什么“看起来只能在微信里”

H5 里 `sCan()`（`chunks/0.js` @2337377）按 `localStorage.platform` 分支：

| 宿主 | 扫码方式 |
| --- | --- |
| 企业微信 | `wx.scanQRCode({needResult:1, scanType:["qrCode"]})` |
| 钉钉 | `ddScan(cb)` |
| 易班 | `encode_fun()` / `loadURL("zsbcAction://richScan?callBack=BackScan")` |
| 今日校园等 | `campus.scan({success})` |

各分支拿到的都是**同一个文本**，然后走同一个回调 `window.BackScan(text)`。也就是说扫码器只是
“把文本递给网页”，与接口无关。这是我们可以自己拍照的全部理由。

## 2. 二维码内容（明文，无加密）

`Encrypt` / `Decrypt` 在 bundle 里都是**恒等函数**：

```js
// chunks 里的 aNLv 模块
e.Encrypt = function (t) { return t }, e.Decrypt = function (t) { return t }
```

生成端（`chunks/8.js` 管理端、`chunks/109.js` 学生端）：

```js
// 签到（动态码，sp 每 10 秒 +10000）
"qutuo://sign?activityId=" + id + "&userId=" + uid + "&sp=" + ts + "&isSignIn=action"
// 签退
"qutuo://sign?activityId=" + id + "&userId=" + uid + "&sp=" + ts + "&isSignIn="
// 学生自己的码（管理员扫学生时用）
"qutuo://waitSign?activityId=" + id + "&userId=" + uid + "&sp=" + ts
```

所有码尾部会再拼 `?yiban=yiban_scan_result`（易班扫码器的标记，微信里原样返回）。

- `sp`（动态码）= `/activity/sign/count` 响应里**服务端**的 `timestamp`，每 10 秒 `+=10000`；
- `sp`（静态码）= 空串；
- `userId` = 生成二维码那台设备登录的用户（管理端显示的活动码里是**管理员**的 id）。

## 3. 提交接口

`POST /activity/sign/in-out`（`chunks/0.js` 最新实现）：

| 扫到的内容 | 请求体 |
| --- | --- |
| 含 `waitSign` | `{id, userId, type: 1, sp}` |
| 含 `sign` 且含 `action` | `{id, userId, type: 2, sp, signSwitch: 1}` → 提示「签到成功」 |
| 含 `sign` 不含 `action` | `{id, userId, type: 2, sp, signSwitch: 2}` → 提示「签退成功」 |

`id` = 二维码里的 `activityId`，`userId` = 二维码里的 `userId`（**原样转发**），
`sp` = 二维码里的 `sp`（静态码时为空串）。成功判据：`code == 0`。

## 4. 定位签到（另一条通道，不需要摄像头）

`POST /activity/sign/map-in-out`：`{id, sp: Date.now(), longitude, latitude, address}`

- `sp` 在这里由**客户端**生成（`(new Date).getTime()`），与扫码通道的“服务端时间”不同；
- `address` 由网页用高德逆地理编码得到（`AMap.Geocoder`），不参与判定也可能参与展示；
- 页面里高德圆形范围默认中心 `117.266683, 31.845858`（淮南校区）、半径 100 米，
  实际值来自活动详情的 `latitude/longitude/ranges`。

管理端批量打卡是另一个路径：`/practice/activity/sign/map-in-out`（多一个 `userIdList`），
属于「实践」模块，**与第二课堂活动无关**，不要混用。

## 5. 本校实测到的开关与字典

`GET /dict/school/system-para/list {fieldType:"activity", keyWord:"sign_way"}`（`code:0`）：

```json
[{"des":"活动管理","name":"动态二维码","id":1},
 {"des":"活动管理","name":"静态二维码","id":2},
 {"des":"活动管理","name":"GPS定位","id":3}]
```

活动数据里（真实响应）：

- `signWay`：逗号分隔的上面这组 id。实测样本 `"1"`（动态二维码）。
- `signSwitch`：`0` 未开启、`1` 签到中、`2` 签退中。实测样本 `0`。
- `attendanceType`：`1` 签到+签退、`2` 仅签到、`3` 仅签退。
- `signAudit`：`1` 需审核；`signLimit`：`0` 不限 / `1` 个人 / `2` 团队。
- `isSigner`（列表项里有）：`1` 表示我已签到。
- 定位相关：`latitude`、`longitude`、`ranges`、`mapClockForceAddressFlag`。

管理端开关：`POST /activity/sign/switch {handleType: 0 关闭 / 1 开启签到 / 2 开启签退, activityId, mapClockFlag}`。

**学生端「扫一扫 / 二维码 / 地图」三个按钮的显示条件**（`chunks/35.js`）：

```
identity == 1(已参加) && applyStatus == 2 && status ∈ {5,6,7,8} && signSwitch != 0
扫一扫 / 二维码：signWay 含 1 或 2     地图：signWay 含 3
```

## 6. 已实测（只读，可放心复用）

| 接口 | 结果 |
| --- | --- |
| `GET /activity/sign/count?id=` | `code:0`，`data:{signInCount, signOutCount, memberCount, activityName}`；**参与者也能调**，响应顶层有服务端 `timestamp` |
| `GET /activity/my-sign-in-out-list` | `code:0`，`data:{total,pageSize,list,pageNum}`（本人无记录时 `total:0`） |
| `GET /dict/school/system-para/list`（sign_way） | 见上表 |

### 6.1 写接口的形状验证（2026-10-22，只针对**不存在的活动 id=1**）

在拿到用户许可后，对 `POST /activity/sign/in-out` 发了两次请求，`id` 都是 1（不存在的活动）：

| 请求 | 响应 |
| --- | --- |
| `{id:1, userId:"0", type:2, sp:"1", signSwitch:1}` | `code:10002 msg:"二维码已失效"` |
| `{id:1, userId:"0", type:2, sp:<当前毫秒>, signSwitch:1}` | `code:10007 msg:"请不要重复提交"` |

结论：

1. **请求体形状是对的**——服务端没有报参数错误，而是进入了业务判断。
2. **`sp` 是纯粹的时效标记**：过期值被「二维码已失效」拒绝；客户端自己生成的“当前时间”能通过时效校验，
   随后才撞上「重复提交」。所以动态二维码防的是截图转发，不防本地伪造时间。
   我们**不**做“不扫码直接签”，因为那不是官方功能。
3. 去重窗口存在（`10007`），短时间内重复提交第二次会被拒；界面直接把服务端 `msg` 显示出来即可。
4. `userId` 传字符串可行（H5 也是字符串）。

## 7. 尚未确认（必须在真实签到窗口里试）

1. 服务端签的是**令牌本人**还是**二维码里的 userId**。按理应是“令牌本人 + 管理员可为他人代签”，
   否则管理员的活动码会把管理员自己签掉——这条决定「能不能代签」。
2. 静态二维码的空 `sp` 是否被接受（官方对静态码就是传空串）。
3. 各种真实失败场景的文案（未开启签到、不在时段、未报名）。
4. 成功响应是否只有 `code:0`，`data` 里有没有签到时间可以回显。

这些只能在“老师真的开了签到”的时候验证。真实签到由用户本人操作，事后看 `[action]` 日志核对。

## 8. 方案对比

| 方案 | 做法 | 优点 | 代价 |
| --- | --- | --- | --- |
| A. 原生扫码（推荐） | CameraX + zxing 解码 → 解析 → POST | 与官方完全等价；一次性投入 | 相机权限、约 +0.5～0.8 MB、需要真机调摄像头 |
| B. 粘贴二维码文本 | 输入框粘贴 `qutuo://…` | 零依赖，适合调试 | 实际场景里拿不到文本（微信不展示扫码结果），只能当兜底 |
| C. 不扫码直接签 | 用当前服务端时间拼 `sp` 直接 POST | 无需相机、无需到场 | **等于绕过考勤**，且未验证；有被认定违规的风险 |
| D. 定位签到 | `/activity/sign/map-in-out` | 覆盖 `signWay=3` 的活动 | 需定位权限 + 逆地理编码；判定权在服务端 |

旧项目 `aust_secondclass` 做了 B 和 D：`SignCode.kt`（解析，纯函数）与 `SignInScreen.kt`
（粘贴框 + 系统定位），摄像头一直没接——所以“旧项目没完成的部分”就是**方案 A**。

## 9. 相机方案的技术选型

- 解码：`com.google.zxing:core:3.5.3`（纯 Java，离线，无 Google Play 依赖，约 500 KB）。
- 取景：`androidx.camera:camera-core/camera2/lifecycle/view`，`ImageAnalysis` 里把 YUV 转灰度再喂
  `MultiFormatReader`（只开 QR，限流到每 200～300 ms 一次，避免烫手）。
- 不选 `zxing-android-embedded`：它自带一个完整 Activity，样式与我们的 Compose 页面割裂，
  而且难以接预测性返回；自己写 100 多行反而更可控。
- 备选：ML Kit 条码（识别更准），但依赖 Google Play 服务，MIUI 上不可靠，放弃。

## 10. 验证计划（不产生真实业务数据）

1. 解析函数单测：三种码 + 易班尾巴 + 静态码空 `sp` + 非法文本。
2. 用 zxing 在本地生成三种码的 PNG，推送到手机，走“从图片解码”的调试入口验证
   **解码 + 解析 + 类型判定** 全链路（不联网）。
3. 失败码探测（需用户同意）：对 `signSwitch=0` 的活动发一次带过期 `sp` 的请求，记录业务码与文案。
4. 真实签到：只能由用户在现场扫，事后看 `[action]` 日志与 `my-sign-in-out-list`。
