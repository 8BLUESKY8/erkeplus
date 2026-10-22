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

## 日志

渠道 `[sign]`，落在 `files/logs/erkepp.log`，现场可直接在
「设置 → 开发者模式 → 签到日志」里一键复制。关键行：

```
scan page open id=… signSwitch=… sandbox=… build=0.6.8/23
camera frame bytes=460800 expected=460800 size=640x480
scan source=camera id=… type=2 signSwitch=1 sp=…
submit start id=… type=… signSwitch=… sp=… userId=…
submit done code=0 msg=ok elapsed=619ms
```
