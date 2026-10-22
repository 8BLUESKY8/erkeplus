# 二课++

安徽理工大学「第二课堂」的非官方 Android 客户端。

官方站点（`https://win.9xueqi.com`）的移动端页面可用但不顺手，本项目在保留其完整功能的前提下重做交互：
高频页面用原生实现，长尾页面回落到 WebView，二者共用同一份登录态。

## 状态

| 版本 | 内容 | 状态 |
| --- | --- | --- |
| v0.1.0 | 原生登录 + 个人信息落地页，网页仅作兜底 | 已发布 |
| v0.2.0 | 原生发现页（活动流、筛选、已报名、我的活动）、设置页、左右滑动切页 | 已发布 |
| v0.3.0 ~ v0.7.2 | 原生详情、作业查看/提交、附件与回收站、扫码签到、首页扫一扫、原生报名与取消报名、打卡记录 | 已发布 |
| v0.8.1 | 应用内更新（下载并安装）、活动分享二维码 | 已发布 |

## 结构

```
android/        Android 工程（Kotlin + Compose）
docs/           项目文档、决策记录、接口验证清单
.tools/         本地脚本（不入库）
tem/            本地参考材料（不入库）
```

## 构建

前置：JDK 17 及以上（推荐 Android Studio 自带的 JBR 21）、Android SDK（platform 36）。

```bat
cd android
build.cmd            :: 产出 debug APK 到 android/out/
build.cmd release    :: 产出签名 release APK
```

`build.cmd` 只是转调 `build.ps1`（并绕过 PowerShell 默认的脚本执行策略），
构建脚本会强制使用 JDK 17+，不依赖系统 PATH 里的 `java`。

签名口令不写在仓库里，读取顺序见 `android/app/build.gradle.kts`。

## 发布与更新

仓库：`git@gitee.com:LeoLin_ME/erkeplus.git`（公开）。

下载：<https://gitee.com/LeoLin_ME/erkeplus/releases>

发新版本的流程：

1. 改 `versionCode` / `versionName`，写 `CHANGELOG.md`；
2. `build.cmd release` 产出签名 APK；
3. 打 `vX.Y.Z` 标签并推送；
4. 在 Gitee 建 Release（tag 与版本号一致），**把 APK 作为附件上传**——应用内更新靠这个附件。

「设置 → 关于 → 检查更新」读 Gitee 公共接口：先看 Release（取其中第一个 `.apk` 附件），
没有 Release 再看 Tag。发现新版本时直接「下载并安装」，下载到 `cache/update/` 后交给系统安装器；
没给「安装未知应用」权限会先把用户送去系统开关。

## 声明

非官方项目，与安徽理工大学及第二课堂平台方无隶属关系。账号密码仅用于官方站点本身，本项目不收集、不上传任何凭据。
