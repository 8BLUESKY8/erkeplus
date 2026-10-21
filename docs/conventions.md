# 工程约定

## 提交信息

遵循 Conventional Commits，正文可用中文：

```
feat: 新增 WebView 外壳
fix: 修正返回键在首页无法退出
docs: 补充接口验证记录
chore: 升级 Compose BOM
```

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
