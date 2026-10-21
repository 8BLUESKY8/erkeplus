# 二课++

安徽理工大学「第二课堂」的非官方 Android 客户端。

官方站点（`https://win.9xueqi.com`）的移动端页面可用但不顺手，本项目在保留其完整功能的前提下重做交互：
高频页面用原生实现，长尾页面回落到 WebView，二者共用同一份登录态。

## 状态

| 版本 | 内容 | 状态 |
| --- | --- | --- |
| v0.1.0 | WebView 外壳：登录、全站可用、原生外框 | 开发中 |
| v0.2.0 | 原生首页、原生登录 | 规划中 |
| v0.3.0 | 原生活动列表与详情 | 规划中 |
| v0.4.0 | 原生二课成绩 | 规划中 |

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

## 声明

非官方项目，与安徽理工大学及第二课堂平台方无隶属关系。账号密码仅用于官方站点本身，本项目不收集、不上传任何凭据。
