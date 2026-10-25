# 活动详情「打开卡一帧」的追踪记录

2026-10-25，真机 24090RA29C（Android 16 / API 36）。

## 结论先说

那一帧是**主线程的「组合 + 测量布局 + 绘制」全堆在同一个帧里**，约 164～248ms。没有单一热点；
期间小核频率还在从 1300MHz 往 2000MHz 爬（大核没参与），所以其中一部分是「静置后第一次重活」
的升频时间，不是纯代码问题。

## 方法

```powershell
# 抓 8 秒 trace，同时用 adb 点开一张活动卡
adb -s <serial> shell atrace -t 8 -b 32768 gfx view sched freq -a gitee.leolin_me.erkepp.debug > trace.txt

# 解析：打印最长的 slice
powershell -File .tools\trace-slices.ps1 -Path trace.txt -Top 20 -Thread <主线程 tid>
```

`gfxinfo framestats` 只能看到「traversal / draw / sync」三段，看不出里面是什么，容易误判成「绘制慢」。
atrace 的 slice 才能指出是测量布局这一段。

## 实测（app 主线程 tid=23302）

| slice | 耗时 |
| --- | --- |
| `Choreographer#doFrame` | 248ms |
| `dispatchFrameCallbacks` | 179ms |
| `traversal` | 166ms |
| `Record View#draw()` | 165ms |
| `AndroidOwner:measureAndLayout` | 164ms |
| └ `Compose:recompose` | 59ms |
| └ `TextLayout:initLayout` | 22ms |
| └ `Compose:applyChanges` | 15ms |

同一窗口内 CPU 频率：小核 1300 → 2000MHz（逐级爬升），大核（2500MHz）全程未参与。

## 已经排除的

- **WebView**：活动介绍早已改成原生富文本，删掉 WebView 后该帧从 233ms 降到 171ms（同进程第二次打开），但没消失。
- **整页一次性组合**：已改成 `LazyColumn` 分块，`Compose:recompose` 仍然 59ms，说明大头不在「组合了多少块」，
  而在「测量布局 + 首次绘制」。
- **图片缓存**：同进程连续打开第二次仍是 171ms，不是「第一次上传大图」。
- **JIT**：热态复现，不是解释执行。
- **封面大图**：把封面解码宽度从 1080 压到 160 再测，首帧仍是 248ms（2026-10-25 实测）。
- **CPU 升频**：在列表滚动中立刻点开（CPU 已热），首帧仍是 200ms。
- **背后列表被连带重组**：在 `DiscoverScreen` 组合入口打日志，点开详情前后计数都是 4，delta = 0。
- **入场透明度导致离屏层**：把 `SecondaryPage` 入场 alpha 去掉（只留位移），首帧仍是 230ms。
- **每帧都慢**：页面上滑 3 次，120 帧里最大 16ms、中位数 7ms。**只有打开后的第一帧慢。**

## 现在的判断

首帧的 164ms 集中在 `AndroidOwner:measureAndLayout`，但**摊在整页的测量布局上，没有单一热点**；
同一次打开的后续帧（滚动）只有 7～16ms。也就是说这是「这个页面第一次上屏」的一次性成本。

继续往下需要 Perfetto 系统 trace + Compose 的自定义 tracing（默认的 atrace 拿不到
「哪个 composable / 哪次测量」级别的信息）。在拿到那层数据之前，不再做猜测性改动。

## 下一步（还没做的）

1. Perfetto 系统 trace + Compose tracing：拿到「哪个 composable、哪次测量」的粒度
2. 若仍无热点，考虑接受现状（打开约 200ms，之后滚动 7～16ms），把精力放回功能

## 复现命令速查

```powershell
adb shell dumpsys gfxinfo <pkg> reset
adb shell input tap <x> <y>          # 打开详情
adb shell dumpsys gfxinfo <pkg> framestats > fs.txt
```
