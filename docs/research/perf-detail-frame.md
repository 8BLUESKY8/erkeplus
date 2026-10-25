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

## 下一步（每条都便宜）

1. ~~在列表滚动过程中立刻点开（CPU 已经热）→ 若长帧消失，说明剩余部分是升频~~ **已试：仍是 200ms，
   所以升频只是次要因素，主因仍在代码侧**
2. 把详情封面的解码宽度从 1080 降到 720 → 若长帧减半，就是大图上传
3. 若前两条都无效，试「首帧只组合封面 + 标题，其余块延后一帧」——用 `LazyColumn` 的
   `item` + 一个 `LaunchedEffect` 控制可见性太绕，更简单的做法是给 `SectionCard` 加淡入延迟

## 目前最可能的方向（待验证）

`Compose:recompose` 59ms 偏大，而这一帧里还有 164ms 的 `AndroidOwner:measureAndLayout`。
疑似根因：`MainActivity` 的覆盖层状态（`detailActivityId`、`showAllOrgs`…）是在**很高的层级**读的，
一旦打开详情，整个内容 lambda 一起失效——背后的发现页列表（9 张卡片）跟着重组和重新测量。

验证方法（一次构建即可）：在 `DiscoverScreen` 的组合入口打一行日志，点开详情，看是否被重组。

若成立，修法是把覆盖层状态改成 `State` 对象传给子 composable，让读取点下沉到子层，
打开详情只让覆盖层自己失效。

## 复现命令速查

```powershell
adb shell dumpsys gfxinfo <pkg> reset
adb shell input tap <x> <y>          # 打开详情
adb shell dumpsys gfxinfo <pkg> framestats > fs.txt
```
