# 工程约定

## 提交信息

遵循 Conventional Commits，正文可用中文：

```
feat: 新增 WebView 外壳
fix: 修正返回键在首页无法退出
docs: 补充接口验证记录
chore: 升级 Compose BOM
```

允许的 type：`feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`；
scope 可选，用小写短名（`organization`、`update`、`api`）。破坏性变更在 type 后加 `!`，正文里写 `BREAKING CHANGE:`。

## 分支与合并

`master` 是唯一主干，随时可构建、可发布。

- **不在 `master` 上直接提交**。任何改动先切主题分支，前缀取自上面那组 type：
  `feat/org-members`、`fix/back-key`、`docs/branching`、`chore/release-0.10.0`。分支名用连字符，不用中文。
- 主题分支短命：做完就合回、删掉，不长期挂着；挂了超过几天的，说明该拆了。
- 合回用 `git switch master && git merge --no-ff <branch>`，保留一个合并点，日后能一眼看出
  「这批改动是一次性进来的」。不 rebase 到 `master` 上再推，那等于直接提交。
- `master` 只允许被两类提交推进：合并提交（`Merge ...`）与发版提交（`chore(release): x.y.z`）。
  其余提交都在主题分支上产生。
- 只在发版时打 tag：`vX.Y.Z`，与 `versionName` 一致。
- 外部贡献走 PR：`.gitee/` 与 `.github/` 各有一份模板，内容保持一致，合并前逐项过检查项。

### 本地钩子

仓库自带 `.githooks/`，克隆后执行一次启用：

```sh
git config core.hooksPath .githooks
```

| 钩子 | 作用 |
| --- | --- |
| `commit-msg` | 校验首行符合 Conventional Commits；`Merge` / `Revert` / `fixup!` 放行 |
| `pre-commit` | 禁止在 `master` 直接提交；拦截 `tem/`、`.tools/`、`docs/_local/`、签名文件、`*.apk`、`*.log`、`local.properties` 的误提交 |
| `pre-push` | 禁止删除或强推 `master`；`master` 上的新增提交必须是合并提交或 `chore(release):` |

需要绕过时显式声明，不要养成习惯：

- `ALLOW_PROTECTED_COMMIT=1 git commit ...` —— 仅发版提交；
- `ALLOW_FORCE_PUSH=1 git push ...` —— 仅确有必要的改历史；
- `git commit --no-verify` / `git push --no-verify` —— 钩子本身坏了的时候。

## 版本

- 语义化版本，`MAJOR.MINOR.PATCH`。
- 每个可安装版本必须同时更新：`app/build.gradle.kts` 的 `versionCode`/`versionName`、`CHANGELOG.md`、git tag。
- 未发布内容写在 `CHANGELOG.md` 的「未发布」段。

## 目录

- `android/` 工程本体，不放置文档。
- `docs/` 项目文档（入库）；`docs/_local/` 私人材料（不入库）。
- 第三方参考、抓包原文放 `tem/`（不入库）。

## 脚本

- `build.ps1` / `build.cmd` 只允许 ASCII 字符：Windows PowerShell 5.1 会按 ANSI 读取无 BOM 的 `.ps1`，中文注释会导致解析失败。
- 构建脚本必须自行固定 JDK，不依赖调用者 PATH 里的 `java`。

## 完成的定义（DoD）

一个功能算完成，必须同时满足：

1. 能构建出 APK；
2. 纯逻辑部分有单元测试且通过；
3. 真机走查通过，异常路径（断网、返回键、旋转）无崩溃；
4. `CHANGELOG.md` 与版本号已更新；
5. 相关文档同步，接口类改动附验证日期。
