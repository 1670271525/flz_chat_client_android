# flz_chat_business 客户端开发对接文档

> 适用对象：Web / Android / iOS / 小程序客户端开发  
> 服务定位：聊天业务服务（用户、好友、会话、消息持久化、文件、动态）  
> 服务地址：`http://{host}:8087`  
> 数据格式：`application/json`

---

## 1. 客户端对接边界

本项目不是长连接服务，客户端需要同时对接两类能力：

1. **HTTP 业务接口（本文档）**：账号、资料、好友、会话、文件、动态，以及媒体消息发送。
2. **Chat 长连接服务（WebSocket/TCP）**：纯文本实时消息、在线推送、离线回放触发。

关键结论：

- 纯文本消息（`type=1`）不走 `POST /api/messages`，由客户端发给 chat 长连接服务，再由 chat 通过 MQ 回调本服务持久化。
- 媒体消息（图片/语音/视频/文件，`type=2/3/4/5`）走本服务 HTTP 接口 `POST /api/messages`。

---

## 2. 通用约定

### 2.1 认证与 Header

- 公开接口外，全部接口都需要：`Authorization: Bearer <accessToken>`
- Token 前缀固定为 `Bearer `（含空格）
- 公开接口：
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/email-code`
  - `POST /api/auth/reset-password`
  - `POST /api/auth/refresh`
  - `GET /actuator/health`

### 2.2 统一响应结构

```json
{
  "code": 20000,
  "message": "success",
  "data": {}
}
```

- `code = 20000` 表示成功
- `code != 20000` 表示失败，`message` 可直接用于用户提示（建议客户端按场景二次转换文案）

### 2.3 分页规范

- 通用参数：`page`（从 1 开始）、`size`（最大 50）
- 通用分页结构：

```json
{
  "total": 123,
  "page": 1,
  "size": 20,
  "records": []
}
```

### 2.4 时间与时区

- 服务端按 `Asia/Shanghai` 输出时间
- 建议客户端统一按 ISO 8601 解析（如 `2026-05-30T22:00:00+08:00`）

---

## 3. 关键状态码与枚举

### 3.1 错误码

- `20000`: 成功
- `20001`: 通用失败
- `40001`: 参数校验失败
- `40100`: 未登录或 token 无效
- `40101`: token 过期
- `40300`: 无权限
- `40400`: 资源不存在
- `40900`: 资源冲突
- `42900`: 触发限流
- `1001`: 用户名或密码错误
- `1002`: 用户不存在
- `500`: 服务端异常

### 3.2 业务枚举

- 会话类型：`1=单聊`，`2=群聊`
- 群成员角色：`1=群主`，`2=管理员`，`3=普通成员`
- 消息类型：`1=文本`，`2=图片`，`3=语音`，`4=视频`，`5=文件`
- 动态可见性：`0=公开`，`1=仅好友`，`2=仅自己`
- 好友申请状态：`0=待处理`，`1=已同意`，`2=已拒绝`
- 好友关系状态：`1=好友`，`2=已删除`，`3=已拉黑`

---

## 4. 关键业务流程（客户端必须遵循）

## 4.1 注册/登录/刷新

1. 注册前先调 `POST /api/auth/email-code`（`scene=REGISTER`）
2. 调 `POST /api/auth/register` 成功后拿到 `token + refreshToken`
3. accessToken 失效时调 `POST /api/auth/refresh`
4. 登出调 `POST /api/auth/logout`

注意：

- 登录失败达到阈值会临时锁定（默认 5 次失败锁 15 分钟）
- 邮箱验证码 60 秒内不可重复发送

## 4.2 会话首页初始化

推荐顺序：

1. `GET /api/users/me` 获取当前用户信息
2. `GET /api/conversations?page=1&size=20` 获取会话列表
3. 用户进入会话后：
   - `GET /api/messages?conversationId=xxx&size=30` 拉历史
   - 已读推进：`PUT /api/conversations/{id}/read`

## 4.3 文本消息发送（经 chat 长连接）

1. 客户端通过 chat 长连接发送文本消息（必须带 `clientMsgId`）
2. chat 服务通过 MQ `business.msg.persist` 通知本服务入库
3. 本服务入库成功后，发布 `chat.msg.send` 由 chat 服务转发

说明：

- 文本消息会经过服务端文本过滤（空内容、超长、敏感词替换）
- 文本最大长度为 2000 字符

## 4.4 媒体消息发送（HTTP）

1. 先上传文件（二选一）：
   - 推荐：`POST /api/files/presign` 获取 `uploadUrl` 后客户端直传
   - 备选：`POST /api/files/upload` 服务端代传
2. 拿到 `objectKey` 后调用 `POST /api/messages`
3. 响应中返回 `messageId`，并可能返回 `downloadUrl`

## 4.5 离线消息回放

- 用户上线时，chat 服务会发送 `business.user.online`
- 本服务按用户未读状态回放消息到 chat（`chat.msg.replay`）
- 客户端收到后应及时推进会话已读指针（`PUT /api/conversations/{id}/read`）

---

## 5. HTTP 接口说明

## 5.1 鉴权模块 `/api/auth`

### 发送邮箱验证码（公开）

- `POST /api/auth/email-code`

请求：

```json
{
  "email": "alice@example.com",
  "scene": "REGISTER"
}
```

约束：

- `scene` 仅支持 `REGISTER`、`RESET_PASSWORD`
- 单邮箱 60 秒内不可重复发送

### 注册（公开）

- `POST /api/auth/register`

```json
{
  "userName": "alice",
  "email": "alice@example.com",
  "phone": "13800000000",
  "password": "P@ssw0rd1",
  "emailCode": "123456"
}
```

字段约束：

- `userName`: 3~64
- `phone`: 可空；非空需匹配 `^1\\d{10}$`
- `password`: 6~32，至少 1 个字母 + 1 个数字

响应 `data`：

```json
{
  "userId": 1001,
  "token": "xxx",
  "refreshToken": "xxx",
  "expireAt": "2026-05-30T22:00:00+08:00"
}
```

### 登录（公开）

- `POST /api/auth/login`

```json
{
  "account": "alice",
  "password": "P@ssw0rd1"
}
```

- `account` 支持用户名/邮箱/手机号

### 刷新 Token（公开）

- `POST /api/auth/refresh`

```json
{
  "refreshToken": "xxx"
}
```

### 忘记密码（公开）

- `POST /api/auth/reset-password`

```json
{
  "email": "alice@example.com",
  "emailCode": "123456",
  "newPassword": "N3wPass123"
}
```

### 登出（需登录）

- `POST /api/auth/logout`

---

## 5.2 用户模块 `/api/users`（需登录）

### 获取我的资料

- `GET /api/users/me`

返回要点：`userId`、`userName`、`email`、脱敏手机号、`information`（头像/昵称/心情/签名/性别/生日/地区）

### 更新我的资料

- `PUT /api/users/me`

```json
{
  "nickname": "Alice",
  "avatarUrl": "public/1001/avatar.png",
  "mood": "HAPPY",
  "signature": "Hello",
  "gender": 2,
  "birthday": "1998-06-01",
  "region": "Shanghai"
}
```

### 查看用户公开资料

- `GET /api/users/{userId}`

说明：

- 目标用户若已拉黑当前用户，返回 `40300`

### 搜索用户

- `GET /api/users/search?keyword=alice&page=1&size=20`

规则：

- 用户名/邮箱模糊匹配，手机号精确匹配

### 修改密码

- `PUT /api/users/me/password`

```json
{
  "oldPassword": "OldPass123",
  "newPassword": "NewPass123"
}
```

---

## 5.3 好友模块 `/api/friends`（需登录）

### 好友列表

- `GET /api/friends?page=1&size=20`

返回字段要点：`userId`、`alias`、`nickname`、`avatarUrl`、`mood`、`signature`

### 发起好友申请

- `POST /api/friends/requests`

```json
{
  "toUserId": 1002,
  "remark": "我是Alice"
}
```

业务约束：

- 不能加自己
- 已是好友返回冲突
- 7 天内同方向待处理申请不可重复发起

### 收到的申请

- `GET /api/friends/requests/incoming?status=0&page=1&size=20`

### 发出的申请

- `GET /api/friends/requests/outgoing?status=0&page=1&size=20`

### 同意申请

- `POST /api/friends/requests/{requestId}/accept`

响应 `data`：

```json
{
  "conversationId": 9001
}
```

说明：

- 同意后会自动创建（或复用）双人单聊会话

### 拒绝申请

- `POST /api/friends/requests/{requestId}/reject`

### 设置备注

- `PUT /api/friends/{friendId}`

```json
{
  "alias": "同事A"
}
```

### 拉黑 / 取消拉黑 / 删除好友

- `POST /api/friends/{friendId}/block`
- `POST /api/friends/{friendId}/unblock`
- `DELETE /api/friends/{friendId}`

---

## 5.4 会话模块 `/api/conversations`（需登录）

### 会话列表

- `GET /api/conversations?page=1&size=20`

返回要点：

- `conversationId`、`type`、`name`、`avatarUrl`
- `lastMessageId`、`lastMessage`（`type/preview/senderId/createdAt`）
- `unreadCount`、`pinned`、`mute`
- 单聊额外返回 `peer`

排序：

- `pinned DESC` + `last_message_at DESC` + `conversation_id DESC`

### 创建单聊

- `POST /api/conversations/single`

```json
{
  "peerUserId": 1002
}
```

说明：若已存在单聊，会直接返回已有 `conversationId`。

### 创建群聊

- `POST /api/conversations/group`

```json
{
  "name": "Holiday",
  "avatarUrl": "public/group/holiday.png",
  "memberIds": [1002, 1003, 1004]
}
```

### 更新群信息

- `PUT /api/conversations/{id}`

```json
{
  "name": "New Group Name",
  "avatarUrl": "public/group/new.png"
}
```

权限：群主/管理员可操作。

### 成员管理

- 添加成员：`POST /api/conversations/{id}/members`
- 移除成员：`DELETE /api/conversations/{id}/members/{userId}`
- 成员列表：`GET /api/conversations/{id}/members?page=1&size=50`
- 设置角色：`PUT /api/conversations/{id}/role`

角色设置请求：

```json
{
  "userId": 1002,
  "role": 2
}
```

限制：

- 仅群主可设置管理员（`role=2`）或普通成员（`role=3`）
- 不能移除群主

### 会话状态

- 退群：`POST /api/conversations/{id}/quit`
- 解散：`POST /api/conversations/{id}/dissolve`（仅群主）
- 标记已读：`PUT /api/conversations/{id}/read`
- 置顶：`PUT /api/conversations/{id}/pin`
- 免打扰：`PUT /api/conversations/{id}/mute`

示例：

```json
{ "lastReadMessageId": 5567 }
```

```json
{ "pinned": true }
```

```json
{ "mute": true }
```

---

## 5.5 消息模块 `/api/messages`（需登录）

### 发送媒体消息（HTTP）

- `POST /api/messages`

```json
{
  "conversationId": 9001,
  "type": 2,
  "content": "chat/1001/2026/05/30/uuid.jpg",
  "mediaMeta": "{\"size\":12345}",
  "clientMsgId": "uuid-xxx"
}
```

约束：

- `type` 仅支持 `2/3/4/5`
- 文本 `type=1` 会被拒绝（请走 chat 长连接）

响应 `data`：

```json
{
  "messageId": 5568,
  "conversationId": 9001,
  "createdAt": "2026-05-30T22:00:00+08:00",
  "downloadUrl": "https://minio/presigned-get"
}
```

### 历史消息

- `GET /api/messages?conversationId=9001&beforeId=5568&size=30`

规则：

- 默认 `size=30`，最大 50
- 倒序返回（新到旧）

### 未读消息

- `GET /api/messages/unread?conversationId=9001`

规则：按消息 ID 正序，最多 200 条。

### 撤回消息

- `POST /api/messages/{messageId}/recall`

限制：

- 仅发送者本人
- 发送后 2 分钟内

### 单边删除消息

- `DELETE /api/messages/{messageId}`

说明：仅对当前用户隐藏，不影响其他成员。

---

## 5.6 文件模块 `/api/files`（需登录）

### 获取上传预签名（推荐）

- `POST /api/files/presign`

```json
{
  "bucket": "chat",
  "filename": "voice.mp3",
  "contentType": "audio/mpeg",
  "size": 12345
}
```

响应：

```json
{
  "objectKey": "chat/1001/2026/05/30/xxxx.mp3",
  "uploadUrl": "https://minio/presigned-put",
  "expireSeconds": 600
}
```

校验：

- `bucket` 仅支持 `chat/public`
- `size` 必须 `1 ~ 10485760`（10MB）

### 获取下载预签名

- `GET /api/files/presign?objectKey=chat/1001/.../xxxx.mp3`

响应：

```json
{
  "url": "https://minio/presigned-get",
  "expireSeconds": 7776000
}
```

### 服务端代传（备选）

- `POST /api/files/upload`（`multipart/form-data`）
- 字段：`bucket`、`file`

返回：`data` 为 `objectKey`。

---

## 5.7 社交动态模块 `/api/social`（需登录）

### 发布动态

- `POST /api/social`

```json
{
  "content": "今天天气真好",
  "visibility": 0,
  "images": [
    {
      "imageUrl": "social/1001/xx1.png",
      "sortOrder": 0,
      "width": 1080,
      "height": 1440
    }
  ]
}
```

### 查看用户动态

- `GET /api/social/users/{userId}?page=1&size=10`

可见性策略：

- 看自己：`0/1/2`
- 看好友：`0/1`
- 看非好友：`0`

### 查看好友动态流

- `GET /api/social/feed?page=1&size=10`

### 删除动态

- `DELETE /api/social/{socialId}`（仅本人）

### 点赞 / 取消点赞

- `POST /api/social/{socialId}/like`
- `DELETE /api/social/{socialId}/like`

---

## 6. 客户端建议实现

- 所有请求统一封装 `Result` 解包层，按 `code` 分流。
- 全局拦截 `40100/40101`：先尝试 `refresh`，失败后回到登录页。
- 消息发送必须携带稳定 `clientMsgId`（UUID），用于幂等与重试去重。
- 媒体上传采用“预签名直传 + 业务入库”两段式，失败可重试第二段。
- 会话页面离开/后台时，及时提交已读指针，减少回放量。

---

## 7. 联调检查清单

- 登录成功后，任意受保护接口能正确识别 token。
- 文本消息仅走 chat 长连接，不直接调用 `POST /api/messages`。
- 媒体消息流程可完整跑通：`presign -> upload -> send message`。
- 好友申请同意后可拿到有效 `conversationId` 并进入单聊。
- 会话已读更新后，`unreadCount` 能正确下降。

