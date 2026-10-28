# 给 AI 助手的说明

本仓库是「二课++」——安徽理工大学「第二课堂」的非官方 Android 客户端。
这一页写给接手开发的 AI 助手；人类读者看 [README.md](README.md)，工程规则看 [docs/conventions.md](docs/conventions.md)。

## 一、动手前的硬规则

1. **改源码只能用编辑工具**（`edit` / `write`）。不要用 PowerShell 的 `Get-Content` / `Set-Content`：
   PS 5.1 按 GBK 读会把中文注释读坏，回写又会加 BOM、改行尾，整个文件 diff 全红。这个坑已经踩过两次。
2. **`.ps1` / `.cmd` 只允许 ASCII**：PS 5.1 会按 ANSI 解析无 BOM 的 `.ps1`，中文注释直接语法错误。
   `.githooks/*` 是 sh 脚本，提示也用 ASCII（Windows 控制台码页 936 会把 UTF-8 输出糊掉），
   中文说明放文档里。
3. **一次只做一件事**，做完通过验证再进入下一件。验证 = 构建 + 单元测试 + 真机走查。
4. **不要擅自发版**。`versionCode` / `versionName`、`CHANGELOG.md` 的正式版本段、git tag、
   Gitee release，只在用户明确要求时动；平时的改动写进 `[未发布]`。
   用户明确抱怨过「频繁发小版本，观感差」。
5. **有后果的操作先问**。提交、推送、发版、改设备设置、删分支，都先给出命令和影响，由用户决定。
   `adb` / `git` 的只读探查可以自行做。
6. **不写没验证过的结论**。接口行为、库行为、系统行为，本机没跑过就别写进文档或注释；
   必要时写「未验证」并注明探查方法。

## 二、目录

| 路径 | 说明 |
| --- | --- |
| `android/` | Android 工程本体，Gradle 多模块：`:app` 与 `:baselineprofile` |
| `android/app/src/main/java/gitee/leolin_me/erkepp/` | 全部 Kotlin 源码 |
| `android/app/src/test/` | JVM 单元测试（28 个文件） |
| `android/app/src/release/` | release 变体覆盖资源 |
| `docs/` | 项目文档，入库 |
| `docs/api/` | 官方接口实测结论，每条带验证日期 |
| `docs/decisions/` | 架构决策记录（ADR），编号递增，只追加不删除 |
| `docs/design/` | 各页面的交互取舍 |
| `docs/plan/` | 待办与规划 |
| `.githooks/` | 提交与推送校验钩子 |
| `tem/`、`.tools/`、`docs/_local/` | 本地材料，已在 `.gitignore` 里，不要入库 |

## 三、常用命令

构建（debug）：

```powershell
cd android
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug --console=plain --no-daemon *> ..\build.log
```

产物在 `android/app/build/outputs/apk/debug/`。
**不要**直接调用 `android/build.ps1`：Gradle daemon 会继承 stdout，工具调用会长时间不返回
（APK 其实已经生成，容易误判成失败）。给用户用的入口仍然是 `build.cmd`
（`build.cmd release` 出签名包，`build.cmd both` 两个都出）。

单元测试：

```powershell
cd android
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest --console=plain --no-daemon *> ..\test.log
```

报告在 `android/app/build/reports/tests/testDebugUnitTest/index.html`。

真机：

```powershell
adb devices
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <serial> shell run-as gitee.leolin_me.erkepp.debug cat files/logs/erkepp.log
```

实测机型 24090RA29C（Android 16 / API 36）。USB 序列号可以直接用；无线调试地址每轮都变，
用完即弃，连不上就请用户给新地址或插 USB。
release 包不往 logcat 写（`AppLog` 里按 `BuildConfig.DEBUG` 分流），只有文件日志。

## 四、提交与分支

- 提交信息遵循 Conventional Commits，正文用中文。允许的 type：`feat` `fix` `docs` `style`
  `refactor` `perf` `test` `build` `ci` `chore` `revert`。
- 不在 `master` 直接提交。切主题分支（`feat/` `fix/` `docs/` `refactor/` `perf/` `test/` `chore/`），
  做完 `git switch master && git merge --no-ff <branch>`，然后删掉主题分支。
- `master` 只允许被合并提交和 `chore(release): x.y.z` 推进；不允许删除与强推。
- 新克隆的仓库要执行一次 `git config core.hooksPath .githooks` 启用钩子。
- 细节见 [docs/conventions.md](docs/conventions.md) 的「分支与合并」。

## 五、文档地图

| 文档 | 什么时候看 |
| --- | --- |
| [GLOSSARY.md](GLOSSARY.md) | 不确定「学时 / 诚信值 / 待处理消息」这类词指什么，或状态编号怎么对应 |
| [docs/api/win-9xueqi-api.md](docs/api/win-9xueqi-api.md) | 改接口相关代码之前；没有验证日期的结论不要当依据 |
| [docs/design/](docs/design) | 改某个页面之前，先看当初为什么这么排 |
| [docs/decisions/](docs/decisions) | 想知道某个结构为什么是这样 |
| [docs/plan/backlog.md](docs/plan/backlog.md) | 想接下一件事 |
| [CHANGELOG.md](CHANGELOG.md) | 确认某个功能在哪个版本进来的 |

## 六、几个坑

- **签名与口令不在仓库里**：keystore 在 `%USERPROFILE%\.android\keystore\erkepp-release.jks`，
  口令在 `%USERPROFILE%\.gradle\gradle.properties`。不要读、不要写、不要往任何文档里贴值。
- **包名硬编码**：`gitee.leolin_me.erkepp`，`applicationId` 与源码 package 同名，
  改包名要同时改目录、靠 AGP 的 `namespace`，不是改一行的事。
- **接口无公开文档**：`params` 是 URL 编码的 JSON，鉴权头是裸 JWT。
  任何接口结论先查 `docs/api/`，没有就自己抓包，别猜。
- **界面文案少用 emoji**，暗色模式是重点适配对象，改 UI 两个主题都要看。
- **单元测试只覆盖纯逻辑**（解析、计算、状态映射），涉及 Compose 与网络的走真机走查。
