# 0003 冷启动优化

- 状态：已采纳
- 日期：2026-10-21

## 背景

Debug 包冷启动 `am start -W` 中位 1649 ms。用 `Startup` 分段计时看到两处大头：
进程起到 `onCreate` 已花 434 ms（类加载与校验），`setContent` 之后到首帧还有 1236 ms。

## 实测

分段计时（调试包，毫秒，从进程启动算起）：

| 标记 | 优化前 | 优化后 |
| --- | --- | --- |
| onCreate 入口 | 466 | 434 |
| AppLog.init | 537 | 470 |
| 仓库装配完成 | 639 | 555 |
| setContent | 892 | 826 |
| session.load | 942 | 881 |
| 首帧 | 2178 | 1617 |

冷启动 `TotalTime` 中位数：

| 构建 | 中位 | 最小值 |
| --- | --- | --- |
| 调试包，优化前 | 1649 ms | 1618 ms |
| 调试包，优化后 | 1449 ms | 1387 ms |
| 发布包，未开 R8 | 965 ms | 580 ms |
| 发布包，R8 + 资源收缩 | 383 ms | 373 ms |
| 发布包 + Baseline Profile（强制 speed-profile 编译后 8 次） | 396 ms | 375 ms |

发布包几组数字测的都是没登录时的登录页路径，比登录后的发现页轻，所以只能横向比，不能当绝对指标。

## Baseline Profile

生成链路已打通并验证：`android/baselineprofile` 模块在真机上跑一遍冷启动和发现页滑动，
产物写入 `app/src/release/generated/baselineProfiles/baseline-prof.txt`（17698 行），
经 R8 重映射后打进 APK，实测 APK 内 `assets/dexopt/baseline.prof` 只有 5.2 KB，
`baseline.profm` 0.6 KB，安装包体积没变（1.57 MB 前后一致）。装好后系统日志确认
`actualCompilerFilter=speed-profile` 生效。

但**在这台设备上没测到收益**：profile 与无 profile 两组中位分别是 396 ms 与 383 ms，差值在噪声范围内。
原因是这个应用的 dex 本身很小（APK 1.57 MB），冷启动时间主要花在进程创建、SharedPreferences/Keystore 读取
和 Compose 首次布局上，这些不是应用自有方法能覆盖的热点。Baseline Profile 真正发力的场景是
「刚装完、系统还没 AOT 编译过」的首次启动，那种场景无法在不丢用户登录态的前提下反复复现。

结论：保留这套能力（代价 6 KB 与一个模块），但不再期待它压冷启动数字。

## 决策

1. 文件日志改成后台单线程写入。以前每次 `AppLog.d` 都在调用线程上开文件、写盘，主线程也躲不开。
2. 设备号改成懒加载（`ApiClient` 收 `() -> String`）。它要读 SharedPreferences，没必要挡在启动路径上。
3. 三个标签页不再一起组合。首帧只画发现页，再隔一帧把另外两页补上，滑动时已经就绪。
4. 发布包开启 R8 与资源收缩。APK 从 7.2 MB 降到 1.57 MB，要加载和校验的类跟着少。
5. 新增 `Startup` 分段计时（仅调试包输出）和 `.tools/startup-bench.ps1`，改动后可复测。
6. 接上 Baseline Profile 生成链路（`android/baselineprofile` 模块，benchmark 1.5.0 才能识别 Android 16 的帧数据）。

## 复测方法

装好包之后，连续冷启动多次取中位数：

```
adb shell am force-stop gitee.leolin_me.erkepp
adb shell am start -W -n gitee.leolin_me.erkepp/gitee.leolin_me.erkepp.MainActivity
```

`TotalTime` 就是从进程启动到首帧的毫秒数。仓库外的本地脚本 `.tools/startup-bench.ps1`
会自动重复 N 次并给出中位数（`.tools/` 不入库）。

重新生成 profile：

```
cd android
.\gradlew.bat :app:generateReleaseBaselineProfile
```

需要真机连接，且屏幕要亮着（休眠状态下 benchmark 无法确认首帧，会报
`Unable to confirm activity launch completion`）。

## 未做

- 会话解密（Keystore，约 55 ms）仍在首帧路径上：把首帧让出去会闪一帧空白，保持系统启动图反而更快。
