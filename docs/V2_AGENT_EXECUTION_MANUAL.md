# FLZ Chat Android v2 Agent 开发执行手册

> 目标：修复当前客户端的数据隔离、智能体会话、好友请求、资料编辑和 UI 体验问题。本文面向后续开发 agent，要求先按本手册执行，不做无关重构。

## 1. 当前结论

当前项目已经具备 Android Java + Room + Retrofit + WebSocket + Agent SSE 的基础结构，但存在明显 v1 风险：

- 本地 Room 数据未按登录用户隔离，同一设备切换账号后会混看会话、消息、好友、动态点赞态和智能体记录。
- 智能体会话在点击“新对话”时立即入库，未聊天也会生成无效会话；本地会话与服务端 `session_id` 的持久化也没有按用户维度管理。
- 好友申请同意失败时 UI 不提示，成功后也没有明确进入单聊或刷新状态，错误被静默吞掉。
- 编辑资料只预填昵称，不预填签名，保存时可能把签名写成空字符串；请求 DTO 不完整，保存态和错误态也不足。
- 多处 UI 是 v1 占位实现，列表空态、加载态、按钮反馈、横向好友申请卡片和聊天输入区域体验较弱。
- Room 使用 `fallbackToDestructiveMigration()`，后续升级会直接清空本地库，不适合继续扩展。

## 2. 执行优先级

按以下顺序处理，避免修一个模块时继续污染数据：

1. 数据隔离和 Room 迁移。
2. 智能体会话创建规则和持久化。
3. 好友申请同意流程。
4. 编辑资料流程。
5. UI 统一优化。
6. 回归测试和联调验证。

## 3. 数据隔离改造

### 3.1 问题定位

相关文件：

- `AppDatabase.java`
- `SessionManager.java`
- `ConversationEntity.java`
- `MessageEntity.java`
- `FriendEntity.java`
- `SocialEntity.java`
- `AgentSessionEntity.java`
- `AgentMessageEntity.java`
- `ConversationDao.java`
- `MessageDao.java`
- `FriendDao.java`
- `SocialDao.java`
- `AgentDao.java`
- `ChatRepository.java`
- `FriendRepository.java`
- `SocialRepository.java`
- `AgentRepository.java`

当前所有缓存表都没有 `ownerUserId`。DAO 查询也没有 `WHERE ownerUserId = :ownerUserId`。其中 `friends` 以 `userId` 做主键，`conversations` 以 `conversationId` 做主键，`social_posts` 以 `socialId` 做主键，都会在多账号登录时互相覆盖或泄露。`agent_sessions` 使用自增主键，但列表查询也不按用户过滤。

### 3.2 目标方案

采用“单数据库 + 每张用户态表增加 ownerUserId”的方案。不要改成每个用户一个数据库文件，除非后端和测试要求明确改变，因为现有仓库层已经依赖全局 `FlzChatApp.get().getDatabase()`。

需要新增字段：

- `ConversationEntity.ownerUserId`
- `MessageEntity.ownerUserId`
- `FriendEntity.ownerUserId`
- `SocialEntity.ownerUserId`
- `AgentSessionEntity.ownerUserId`
- `AgentMessageEntity.ownerUserId`

说明：

- `social_posts` 虽然是公共动态，但 `liked` 是当前用户态，必须按 `ownerUserId` 隔离。
- `agent_messages` 同时保留 `sessionId` 和 `ownerUserId`，便于防止误查其它账号的本地 session。
- 所有 Repository 写库时统一从 `SessionManager.getUserId()` 取 `ownerUserId`。

### 3.3 DAO 改造要求

所有列表、详情、更新、删除都加用户过滤：

- `ConversationDao.getAll(ownerUserId)`
- `ConversationDao.updateLastMessage(ownerUserId, conversationId, ...)`
- `ConversationDao.incrementUnread(ownerUserId, conversationId)`
- `ConversationDao.clearUnread(ownerUserId, conversationId)`
- `MessageDao.getByConversation(ownerUserId, conversationId)`
- `MessageDao.countByMessageId(ownerUserId, conversationId, messageId)`
- `MessageDao.findByClientMsgId(ownerUserId, clientMsgId)`
- `FriendDao.getAll(ownerUserId)`
- `SocialDao.getAll(ownerUserId)`
- `SocialDao.updateLike(ownerUserId, socialId, ...)`
- `AgentDao.getSessions(ownerUserId)`
- `AgentDao.getSession(ownerUserId, sessionId)`
- `AgentDao.getMessages(ownerUserId, sessionId)`
- `AgentDao.updateSession(ownerUserId, sessionId, ...)`
- `AgentDao.setRemoteSessionId(ownerUserId, sessionId, ...)`

建议索引：

- `conversations`: composite index `(ownerUserId, conversationId)` unique
- `messages`: indexes `(ownerUserId, conversationId)`, `(ownerUserId, clientMsgId)`
- `friends`: composite unique `(ownerUserId, userId)`
- `social_posts`: composite unique `(ownerUserId, socialId)`
- `agent_sessions`: index `(ownerUserId, updatedAt)`
- `agent_messages`: index `(ownerUserId, sessionId, createdAt)`

### 3.4 Migration 要求

将 Room 版本升级到 `3`，移除 `fallbackToDestructiveMigration()`，添加显式 `Migration(2, 3)`。

迁移策略：

- 对已有 v2 本地数据无法可靠判断归属，开发阶段可迁到 `ownerUserId = 0` 并在首次登录后清理 `ownerUserId = 0`。
- 如果产品接受升级清空旧缓存，也要显式执行 `DELETE`，不要继续依赖 `fallbackToDestructiveMigration()`。
- 新增 DAO 方法 `deleteAllForOwner(ownerUserId)`，用于账号退出或用户选择清理缓存。

验收标准：

- 账号 A 登录产生会话、好友、动态点赞和 Agent 对话后退出。
- 账号 B 登录不能看到账号 A 的任何本地聊天、好友、Agent 会话或点赞态。
- 账号 A 再登录，能看到账号 A 自己的本地缓存或后端重新同步后的数据。

## 4. 智能体会话 v2

### 4.1 问题定位

相关文件：

- `AgentListFragment.java`
- `AgentChatActivity.java`
- `AgentRepository.java`
- `AgentDao.java`
- `AgentSessionEntity.java`
- `AgentMessageEntity.java`
- `AgentSseClient.java`
- `docs/CLIENT_API_AGENT.md`

当前 `AgentListFragment` 点击新对话时立即调用 `repo.createSession("新对话", "chat")`，因此不发送任何消息也会生成一条会话。`AgentRepository.createSession()` 同时立即生成 `remoteSessionId`。这与“聊天过才算有效会话”的需求不一致。

另外，当前客户端只维护本地 Room 会话和消息。`flz_agent` 文档说明服务端会按 `session_id` 管理历史，但没有提供会话列表和历史查询 REST API。因此如果需求中的“保存至数据库”指服务端数据库，客户端无法单独完成，需要后端补 API 或确认 `flz_agent` 已落库可查。

### 4.2 目标行为

- 点击“新对话”只打开草稿聊天页，不创建 Room session。
- 第一次成功发送用户消息时才创建本地 `agent_sessions`。
- `remoteSessionId` 在第一次发送时生成并绑定。
- 会话标题使用第一条用户消息截断生成。
- 只有包含至少一条用户消息的会话才出现在 Agent 会话列表。
- 切换 `agentType` 只影响当前草稿或当前有效 session，不产生空 session。

### 4.3 客户端改造步骤

1. `AgentListFragment`
   - 新对话点击不调用 `createSession()`。
   - 启动 `AgentChatActivity` 时传 `EXTRA_SESSION_ID = 0`，`EXTRA_TITLE = "新对话"`。

2. `AgentChatActivity`
   - 支持 `sessionId == 0` 的草稿状态。
   - 本地保存 `pendingAgentType`，在草稿状态切换 chip 时只更新内存。
   - 点击发送时调用新的 `repo.sendMessageCreatingSessionIfNeeded(sessionId, pendingAgentType, text, listener)`。
   - Repository 返回真实 `sessionId` 后，Activity 更新字段，后续消息复用该 session。

3. `AgentRepository`
   - 新增“按需创建 session 并发送”方法。
   - 创建 session、插入用户消息、插入 assistant 占位消息应尽量在同一 IO 流程中完成。
   - `createSession()` 不再暴露给 UI 直接用于“新建空会话”。
   - `getSessions()` 查询必须按 `ownerUserId` 过滤，并只返回有效 session。

4. `AgentDao`
   - 新增 `getSessions(ownerUserId)`，排除没有消息的 session。
   - 可使用 `EXISTS (SELECT 1 FROM agent_messages WHERE ...)` 或在 `agent_sessions` 增加 `messageCount` 字段。

### 4.4 服务端持久化决策

如果 v2 要求 Agent 会话保存到业务数据库并支持跨设备恢复，需要新增服务端接口。建议由业务服务提供代理 API，而不是客户端直接访问 Agent 内部库：

- `POST /api/agent/sessions`
- `GET /api/agent/sessions?page=1&size=20`
- `GET /api/agent/sessions/{sessionId}/messages`
- `POST /api/agent/chat/sse`
- `DELETE /api/agent/sessions/{sessionId}`

服务端表建议：

- `agent_session`: `id`, `owner_user_id`, `remote_session_id`, `agent_type`, `title`, `message_count`, `created_at`, `updated_at`
- `agent_message`: `id`, `session_id`, `owner_user_id`, `role`, `content`, `status`, `created_at`

客户端在服务端 API 未完成前，必须至少保证本地 Room 按用户隔离，不产生空会话。

### 4.5 验收标准

- 点击“新对话”后直接返回，Agent 列表没有新增会话。
- 进入“新对话”发送第一条消息后，列表新增一条标题为首条消息摘要的会话。
- 同一账号重进 App 后可以看到本地 Agent 会话和消息。
- 切换账号后不能看到另一个账号的 Agent 会话。
- SSE 失败走离线兜底时，也算有效会话，因为已经有用户消息。

## 5. 好友申请同意流程

### 5.1 问题定位

相关文件：

- `FriendManageActivity.java`
- `FriendRequestAdapter.java`
- `FriendRepository.java`
- `ApiService.java`
- `ChatDtos.java`
- `activity_friend_manage.xml`
- `item_friend.xml`

接口定义与文档一致：`POST /api/friends/requests/{requestId}/accept` 返回 `conversationId`。当前主要问题是 UI 和错误处理：

- `onError()` 空实现，用户看不到失败原因。
- 同意成功后只 `loadData()`，没有明确提示，也没有进入返回的单聊。
- 按钮没有 loading/禁用态，用户可能重复点击。
- 好友申请使用通用 `item_friend.xml`，只显示“来自用户 {fromUserId}”，没有优先显示 `fromNickname`。
- 拒绝操作藏在长按里，不符合直觉。

### 5.2 改造步骤

1. `FriendRequestAdapter`
   - 使用独立布局 `item_friend_request.xml`。
   - 显示 `fromNickname`，为空再显示 `fromUserId`。
   - 同时提供“同意”和“拒绝”按钮，不再依赖长按拒绝。
   - 点击后禁用当前 item 按钮，等待回调。

2. `FriendManageActivity`
   - 同意成功后：
     - Toast “已同意好友申请”。
     - 调用 `loadData()` 刷新好友和申请列表。
     - 使用返回的 `conversationId` 进入 `ChatActivity`，标题优先使用 `fromNickname`。
   - 同意失败后：
     - Toast 或 Snackbar 显示后端 message。
     - 恢复按钮可点击。
   - 拒绝成功后提示并刷新。
   - 所有空 `onError()` 都要补充用户可见提示和日志。

3. `FriendRepository`
   - `loadIncoming()` 传参保持 `status=0&page=1&size=20`。
   - 对 `data == null`、`conversationId <= 0` 做明确错误。
   - 接受成功后可立即触发 `syncFriends()`，减少 UI 延迟。

### 5.3 验收标准

- B 向 A 发送好友申请，A 可以在好友管理页看到昵称、备注、同意/拒绝按钮。
- A 点击同意后收到成功提示，申请从列表移除，好友列表出现 B，并进入单聊页。
- 后端返回错误时，页面显示具体错误，不会静默失败。
- 重复点击不会发出多次同意请求。

## 6. 编辑资料流程

### 6.1 问题定位

相关文件：

- `EditProfileActivity.java`
- `ProfileFragment.java`
- `UserRepository.java`
- `UserDtos.java`
- `ApiService.java`
- `activity_edit_profile.xml`

当前编辑页只从 `SessionManager` 预填昵称，不拉取完整 `GET /api/users/me`，所以签名输入框为空。用户只修改昵称并保存时，`signature` 会以空字符串提交，可能导致签名被清空。`UpdateMeRequest` 只包含 `nickname/signature/mood/region`，缺少文档中的 `avatarUrl/gender/birthday`。保存时没有 loading 状态，也没有防重复提交。

### 6.2 改造步骤

1. `UserDtos.UpdateMeRequest`
   - 补齐文档字段：`avatarUrl`, `mood`, `signature`, `gender`, `birthday`, `region`, `nickname`。
   - 对未编辑字段传 `null`，不要用空字符串覆盖已有服务端值。

2. `EditProfileActivity`
   - 进入页面后调用 `userRepo.fetchMe()` 预填昵称、签名、心情、地区等已支持字段。
   - 保存前校验昵称非空、长度合法。
   - 如果签名未编辑且服务端原值存在，提交原值或不提交该字段。
   - 保存按钮增加 loading/禁用态。
   - 成功后使用服务端返回的 `UserMe` 更新 `SessionManager`，不要只保存输入框中的 nickname。
   - 失败显示后端 message。

3. `ProfileFragment`
   - `onResume()` 时重新拉取 `GET /api/users/me`，保证编辑完成返回后刷新。
   - 错误时显示轻量提示或保留缓存文案。

### 6.3 验收标准

- 进入编辑页能看到当前昵称和签名。
- 只修改昵称不会清空签名。
- 保存成功后返回“我”页面，昵称和签名立即刷新。
- 网络失败、鉴权失败、参数错误都能看到提示。

## 7. UI 优化范围

### 7.1 必改项

- 好友申请列表改为垂直卡片或清晰横向卡片，不再复用好友 item。
- 所有网络按钮有 loading/disabled 状态。
- Agent 列表增加空态：“还没有对话，发送第一条消息后会保存在这里”。
- Agent 草稿聊天页标题显示“新对话”，发送后更新为首条消息摘要。
- 编辑资料页增加 toolbar 标题、输入框间距、保存按钮状态。
- 聊天/Agent 输入区适配软键盘，避免按钮拥挤。

### 7.2 可选项

- 统一错误展示为 Snackbar。
- RecyclerView item 增加点击态 foreground。
- Agent 消息气泡区分 streaming/error 状态。
- 好友申请显示备注、申请时间和头像占位。

## 8. 禁止事项

- 不要继续新增未按 `ownerUserId` 过滤的 DAO 查询。
- 不要通过点击“新对话”直接创建 Room session。
- 不要吞掉 `onError()`。
- 不要保留 `fallbackToDestructiveMigration()` 作为正式迁移方案。
- 不要为兼容 v1 空会话添加复杂兼容层；v2 可以直接清理无消息 session。
- 不要修改与本任务无关的登录注册、WebSocket 协议和社交发布流程。

## 9. 回归测试清单

### 9.1 多账号隔离

1. A 登录，产生聊天会话、消息、好友、动态点赞、Agent 对话。
2. A 退出，B 登录。
3. B 本地列表不出现 A 的任何数据。
4. B 产生数据后退出，A 再登录。
5. A 和 B 的缓存互不串扰。

### 9.2 智能体

1. 点击新对话后返回，列表不新增。
2. 点击新对话并发送“你好”，列表新增一条有效会话。
3. 断网或 Agent 服务不可达时，离线兜底回复正常入库。
4. 切换 agent 类型后发送，session 的 `agentType` 正确。

### 9.3 好友

1. 收到申请后能显示昵称和备注。
2. 同意后进入单聊，好友列表刷新。
3. 拒绝后申请消失。
4. 后端错误能显示 message。

### 9.4 资料编辑

1. 编辑页预填当前资料。
2. 只改昵称不清空签名。
3. 保存成功后“我”页面刷新。
4. 保存失败不退出页面，按钮恢复。

## 10. 建议提交拆分

建议分 4 个提交或 4 个独立开发步骤：

1. `fix: isolate local room cache by owner user`
2. `fix: create agent sessions only after first message`
3. `fix: complete friend request accept and profile editing flows`
4. `polish: improve empty/loading states for v2 screens`

每个步骤完成后至少运行：

```powershell
.\gradlew.bat assembleDebug
```

如果修改了 Room schema，必须额外做安装升级验证和多账号切换验证。
