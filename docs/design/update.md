# 应用内更新（v0.8.0）

```
设置 → 关于 → 检查更新
```

1. `GET https://gitee.com/api/v5/repos/LeoLin_ME/erkeplus/releases?per_page=5`
   （公开接口，不需要令牌）；取第一条能解析成版本号的 Release，从 `assets[]` 里挑第一个
   `browser_download_url` 以 `.apk` 结尾的附件。没有 Release 就退回去看 Tag。
2. 有更新时弹窗展示说明；点「下载并安装」：
   - 先检查 `canRequestPackageInstalls()`，没权限就把用户送到
     `ACTION_MANAGE_UNKNOWN_APP_SOURCES`；
   - 下到 `cache/update/erkepp-<version>.apk`（流式写盘，边下边报进度，上限 80 MB）；
   - 用 FileProvider（`<package>.files`，`cache-path name="erkepp_update"`）生成
     `content://` URI，交给系统安装器 `ACTION_VIEW` + `application/vnd.android.package-archive`。
3. 失败一律把原因显示在弹窗里（HTTP 码、文件为空、超时……），日志渠道 `[update]`。

## 发布一个可更新的版本

```bat
cd android
build.cmd release
```

然后在 Gitee 建 Release（tag 必须与 `versionName` 一致），**把 APK 作为附件上传**。
没上传附件的 Release 只能「去发布页」，应用内下载会没有入口。

## 实测记录（2026-10-22）

- 装 0.8.0 → 检查更新 → 发现 0.8.1 → 下载 1.93 MB / 2.4 s → 系统安装器 → 安装完成
  → `versionName=0.8.1`，`lastUpdateTime=18:38:06`。

```
[update] download start url=https://gitee.com/.../erkepp-0.8.1.apk -> erkepp-0.8.1.apk
[update] download done erkepp-0.8.1.apk 1930383 bytes
[update] install erkepp-0.8.1.apk
```
