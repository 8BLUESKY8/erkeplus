# 官方站点接口实测记录

- 目标站点：`https://win.9xueqi.com`
- 验证日期：2026-10-21
- 验证方式：在浏览器已登录会话中执行只读 `GET`，未提交任何写操作
- 结论状态：**可用**，但参数必须逐接口锚定

## 基础事实

| 项 | 结论 |
| --- | --- |
| 技术栈 | Vue2 + Vant/VUX 的移动端 H5，hash 路由，200+ 页面 |
| 接口前缀 | `https://win.9xueqi.com/api/app/client/v1/` |
| 请求体 | 统一 `params=<JSON>`，**双重 URL 编码**（`encodeURIComponent` 两次） |
| 鉴权 | 请求头 `Authorization: <JWT>`，**无 `Bearer` 前缀**；token 同时以 query 参数携带 |
| 公共参数 | `token`、`platform=3`、`version=2.0.5`、`device`、`timestamp` |
| 会话存储 | 仅 `localStorage.Token`，无 Cookie |
| 验证码 | 未发现 |
| 请求签名 | 未发现；站内 `Encrypt/Decrypt` 为恒等函数 |
| 登录 | `POST /token`，字段 `schoolCode`、`code`、`password`、`openId`；成功返回 `data.access_token` |

## 已实测端点

| 端点 | 方法 | 结果 | 说明 |
| --- | --- | --- | --- |
| `/student/user/my-info` | GET | `code:0` | 返回学号、学分等个人信息，字段真实可用 |
| `/message/notice/un-read-count` | GET | `code:0` | 返回 `unReadCount` |
| `/page/activity/list` | GET | `code:10002 参数错误` | 需完整参数（旧项目记录为 11 个必填项），待逐项锚定 |
| `/student/credit/list` | GET | `code:404` | 路径不存在，旧项目中的同类记录不可信 |
| `/score/list` | GET | `code:404` | 路径不存在 |
| `/login/banner` | GET | `code:10000` | 空参数不成立，需按页面实际参数调用 |

## 待验证

- `/page/activity/list` 的完整必填参数集合
- 成绩页（`#/scoreReport`）的实际接口路径
- 报名、签到等写操作端点（未验证，v0.2 之后再处理）
