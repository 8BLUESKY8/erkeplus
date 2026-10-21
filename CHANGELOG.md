# 更新日志

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [未发布]

### 新增

- 原生登录页：学号 + 密码，固定安徽理工大学（接口保留多学校扩展点）
- 登录请求按官方 H5 规则编码（`params` 双重 URL 编码）并携带公共参数
- 登录态使用 Android Keystore 中的 AES-256/GCM 密钥加密后本地保存
- 登录后落地页展示姓名、学院、专业、学分、学时
- 退出登录清除本地会话

### 变更

- WebView 外壳由「首屏」降级为「打开完整功能」的兜底入口

### 工程

- 新增 OkHttp、kotlinx-serialization-json、kotlinx-coroutines
- 参数编码、域名白名单均有纯 JVM 单元测试
- Gradle 版本目录、构建脚本固定 JDK、签名材料不入库
