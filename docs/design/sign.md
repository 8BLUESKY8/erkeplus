# 扫码签到（v0.6.7）

官方把这个动作交给宿主 App 的扫码 bridge（企业微信 `wx.scanQRCode`、钉钉 `ddScan`、易班
`richScan`），网页只处理「扫到的文本」。这里把前半段补上：自己开相机解二维码，后半段
**完全照官方实现**。接口与字段的依据见 [签到签退研究记录](../research/checkin.md)。

## 入口

活动详情 → 签到卡片 → 「扫码签到」。

卡片显示：签到方式（字典里解析出来的名字）、当前状态（未开启 / 签到中 / 签退中）、
我的状态（已签到）、已签人数（签到 N · 签退 M）。

按钮出现条件与官方 H5 的 `扫一扫` 逐条对齐：

```
identity == 1 && applyStatus == 2 && status ∈ 5..8 && signSwitch != 0
&& signWay 含 1 或 2（动态 / 静态二维码）
```

不满足时给一句人话解释（老师还没开启签到 / 报名通过后才能签到 / 这个活动用定位签到），
而不是给一个点不动的按钮。

## 扫码页

- 全屏相机预览，中间四个角括号（不做整块遮罩，暗处也能看清码），底部一句提示。
- 右上角手电筒；退到后台释放相机，回来自动重开。
- 解码：camera1 取 NV21 预览帧 → zxing `PlanarYUVLuminanceSource` → 只认 QR；
  每 120 ms 最多解一次，命中后停止解码，预览继续亮着。
- 扫到非签到码：底部提示「这不是活动签到码」，继续扫。
- 扫到签到码：弹确认卡。

## 确认与提交

```
qutuo://waitSign?...              → type=1，不带 signSwitch
qutuo://sign?...&isSignIn=action  → type=2，signSwitch=1（签到）
qutuo://sign?...&isSignIn=        → type=2，signSwitch=2（签退）
```

POST `activity/sign/in-out {id, userId, type, sp, signSwitch?}`，`userId` 与 `sp` 原样转发。

确认卡显示：活动名（不是本活动时去查一次名字）、这次要提交的动作；若扫到的码与活动当前
`signSwitch` 相反（例如老师开着签到却扫到签退码），加一句「可能是上一轮留下的」——只是提醒，
不拦截，因为详情数据可能已经过时。

提交成功 → 用「签到成功 / 签退成功」说话（服务端只回 `ok`），并让详情重新拉一次；
失败 → 直接显示服务端 `msg`（实测见过「二维码已失效」「请不要重复提交」）。

## 没有做的

- **定位签到**（`signWay=3`）：官方学生端只展示地图信息，没有提交入口，不做。
- **不扫码直接签**：`sp` 可用本地时间通过时效校验，技术上可行，但这不是官方功能，不做。
- **我的签到码**（生成 `qutuo://waitSign` 给管理员扫）：需要二维码生成与动态刷新，
  等确认你们活动用「管理员扫学生」再加。

## 首页「扫一扫」（v0.7.2）

官方主页右上角那个扫码是**通用入口**，扫到什么都往 `window.BackScan` 里丢，再按内容分流。
这里照做，入口在发现页顶部右侧：

| 扫到的东西 | 后续 |
| --- | --- |
| `qutuo://sign?…` / `qutuo://waitSign?…` | 进确认框，走与详情页同一套签到逻辑 |
| `…?sourceName=schActivityCode@Xj&activityid=<id>` | 解析出活动 id，直接打开详情 |
| 含 `joinOrganization` 的组织码 | 提示「组织码暂不支持，请到官方页面加入」 |
| 其它 | 「这不是活动签到码」 |

分流规则在 `data/ScanRouter.kt`，有单元测试兜着（签到码里也有 `activityId=`，
不能被误判成分享码）。

## 打卡记录（v0.7.2）

签到卡片里的「打卡记录」，对应官方右上角菜单的同名项：

- 请求：`POST /activity/sign/one/list {userId, activityId}`
- 字段：`type`（1 签到、其它签退）、`signTime`（毫秒）、`isSupplement`（1 补签）
- 入口条件与官方一致：`applyStatus=2` 且 `attendanceType != 4`
- 空列表显示官方的原话「暂无签到签退记录！」

## 离线沙盒（v0.6.8，开发者模式）

现场只有一次机会，所以先在本机把整条链路跑一遍。

- 入口：设置 → 关于 → 开发者模式打开 → 「签到沙盒」。
- 可配置：活动名称、活动 id、活动状态（5～8）、报名状态、签到开关（0/1/2）、
  签到方式（1 动态 / 2 静态 / 3 GPS）、考勤标准、我是否已签到、模拟提交结果（成功，
  或指定 code + msg）。
- 「打开模拟活动详情」渲染的是一张本地详情页（`detailOverride`），底部会联网的动作条被隐藏，
  签到卡片与真实页面完全一致。
- 里头的「扫码签到」走的是同一套相机、解析、确认、结果逻辑；提交由 `sandboxSubmit` 本地
  造结果并写日志，**不发任何请求**。沙盒标题带「（沙盒）」后缀，避免和真实签到混淆。
- 提交成功后详情会立刻变成「已签到」，但只在本次会话生效，重开应用回到配置值。

配套测试二维码由 `.tools/make-qr.java` 生成（JDK 单文件运行，不进 App）：

| 文件 | 内容 | 扫到后 |
| --- | --- | --- |
| `check-in.png` | `qutuo://sign?...&isSignIn=action` | type=2 signSwitch=1，签到 |
| `check-out.png` | `qutuo://sign?...&isSignIn=` | type=2 signSwitch=2，签退（会触发“上一轮的码”提醒） |
| `wait-sign.png` | `qutuo://waitSign?...` | type=1，签到/签退 |
| `not-a-sign-code.png` | 普通网址 | 提示“这不是活动签到码” |

## 远距离与设备差异（v0.7.0）

码可能投在礼堂大屏上，也可能拿在别人的旧手机上，所以相机这一层**不写死任何参数**：

| 参数 | 怎么定 |
| --- | --- |
| 预览尺寸 | 从设备 `supportedPreviewSizes` 里挑：先要长宽比接近屏幕，再挑最大的（上限约 2400×1080），都没有就选最接近的比例，宁可留黑边 |
| 显示方向 | `CameraInfo.orientation` 与屏幕旋转算出来，横屏、平板、异形屏都能摆正；转屏后重开相机 |
| 对焦 | 连续对焦 → 连续视频对焦 → 自动/微距（每 1.5 秒触发），外加点按对焦 |
| 解码节流 | 按实测解码耗时自适应：<80ms 每 180ms 解一次，>350ms 降到 600ms |
| 变焦 | 只在 `isZoomSupported` 时生效，双指放大；不支持就不显示倍率 |

三级兜底：

1. **预览解码**（2340×1080 级别，每帧解 Y 平面，缓冲复用）
2. **双指变焦**：同样分辨率只覆盖更窄视野，码上像素密度直接上升
3. **拍一张**：4096×3072 静帧，先整幅降采样解一次，不中再解中心 50% 的原分辨率区域
   （等价于对静帧做 2 倍数字变焦）；超时 8 秒自动恢复预览

再往下还有开发者模式的「粘贴二维码文本」「从图片识别」。

### 换手机怎么自检

打开开发者模式 → 签到沙盒 → 扫码签到，看「签到日志」里的这几行：

```
camera open size=… view=… continuousFocus=… maxZoom=… previews=… pictures=…
camera frame bytes=… size=…
decode …ms → throttle …ms
```

第一行能看出这台机器给了哪些尺寸、支不支持变焦与连续对焦；`frame bytes` 与 `size`
对不上说明驱动给的帧和声明尺寸不一致（代码会直接跳过该帧并记一条 too short）。
拍一张时还会有 `still shot …` 与 `still center …` 两行。


渠道 `[sign]`，落在 `files/logs/erkepp.log`，现场可直接在
「设置 → 开发者模式 → 签到日志」里一键复制。关键行：

```
scan page open id=… signSwitch=… sandbox=… build=0.6.8/23
camera frame bytes=460800 expected=460800 size=640x480
scan source=camera id=… type=2 signSwitch=1 sp=…
submit start id=… type=… signSwitch=… sp=… userId=…
submit done code=0 msg=ok elapsed=619ms
```
