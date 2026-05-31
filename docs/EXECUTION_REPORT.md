# FLZ Chat 客户端开发执行报告

> 执行日期：2026-05-30  
> 依据文档：`docs/CLIENT_API.md`、`docs/CLIENT_DEVELOPMENT_GUIDE.md`  
> 工程路径：`e:\AndroidProject\flz_chat`

---

## 1. 任务目标

在空白 Android 工程基础上，按课设深度实现带 **智能体（Agent）** 的聊天客户端：

- 用户 **必须登录** 后才能使用全部功能；
- 底部导航：**聊天 / 智能体 / 社交 / 我**；
- 对接 **业务 HTTP**（`flz_chat_business`）与 **Chat WebSocket**（`flz_chat`）；
- 服务端数据 **落地 SQLite（Room）**；
- UI：**轻盈极简**（浅灰绿配色、无 ActionBar、圆角卡片与气泡）。

---

## 2. 架构概览

```
┌─────────────────────────────────────────────────────────┐
│  UI 层                                                   │
│  Login / Register → MainActivity(4 Tab) → 子页面         │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  Repository：Auth / Chat / Friend / Social / Agent / User │
└───────┬───────────────────────────────┬─────────────────┘
        │                               │
┌───────▼────────┐              ┌───────▼────────┐
│ Retrofit HTTP  │              │ WsChatManager  │
│ :8087 业务 API │              │ :8071 /flz/chat│
└───────┬────────┘              └───────┬────────┘
        │                               │
        └───────────────┬───────────────┘
                        │
                ┌───────▼────────┐
                │ Room (SQLite)  │
                │ flz_chat.db    │
                └────────────────┘
```

---

## 3. 功能与文档映射

| Tab / 模块 | 实现类 | 对接能力 | 本地存储 |
|---|---|---|---|
| 登录注册 | `LoginActivity`, `RegisterActivity` | `POST /api/auth/login`, `register`, `email-code` | `SharedPreferences`（token） |
| 聊天 | `ConversationListFragment`, `ChatActivity` | `GET /api/conversations`, `GET /api/messages`, WS `msg.send` / `msg.new` | `conversations`, `messages` |
| 智能体 | `AgentListFragment`, `AgentChatActivity` | 本地 `LocalAgentEngine`（课设规则回复） | `agent_sessions`, `agent_messages` |
| 社交 | `SocialFeedFragment`, `PostSocialActivity` | `GET /api/social/feed`, `POST /api/social`, like | `social_posts` |
| 我 | `ProfileFragment` 等 | `GET/PUT /api/users/me`, 好友 CRUD | `friends` + 会话缓存 |

**登录守卫**：`AuthGuard` 在未登录时拦截业务页并跳转 `LoginActivity`。

**长连接**：`WsChatManager` 实现 `auth_ok`、25s `ping`、文本 `msg.send`、`msg.new`/`msg.replay` 入库、`msg.ack`、被踢 `kicked` 处理。

---

## 4. 关键技术选型

| 类别 | 选型 | 说明 |
|---|---|---|
| 网络 | Retrofit 2.9 + OkHttp 4.9 | 统一 `ApiResult`，401 尝试 `refresh` |
| 实时 | OkHttp WebSocket | URL：`ws://{host}:{port}/flz/chat?token=` |
| 数据库 | Room 2.6 | 文件 `flz_chat.db`，破坏性迁移（课设） |
| UI | Material + 自定义浅色主题 | `colors.xml` 墨绿主色 |

**默认服务地址**（`app/build.gradle` `BuildConfig`）：

- 业务 HTTP：`http://10.0.2.2:8087`（模拟器访问宿主机）
- WebSocket：`ws://10.0.2.2:8071/flz/chat`

真机调试请将 `WS_HOST` / `BUSINESS_BASE_URL` 改为电脑局域网 IP。

---

## 5. 智能体说明

业务文档未提供独立 Agent HTTP 接口。课设实现为：

- **`LocalAgentEngine`**：关键词规则回答（登录、WS 文本消息、好友、会话、社交、心跳、存储等）；
- 对话记录保存在 **`agent_messages`**，与真人聊天数据隔离；
- 生产可替换为 LLM API，仅需改 `AgentRepository.sendUserMessage` 内回复来源。

---

## 6. 目录结构（主要新增）

```
app/src/main/java/com/flz/flz_chat/
├── FlzChatApp.java
├── session/SessionManager.java
├── data/
│   ├── local/          # Room 实体与 DAO
│   ├── remote/         # ApiService, Retrofit, WsChatManager
│   └── repository/
├── agent/LocalAgentEngine.java
└── ui/
    ├── auth/
    ├── main/
    ├── chat/
    ├── agent/
    ├── social/
    └── profile/
```

各模块核心类均含 **简要中文注释**（职责、协议对应关系）。

---

## 7. 构建与联调

### 7.1 构建结果

```text
.\gradlew.bat assembleDebug
BUILD SUCCESSFUL
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 7.2 环境要求

- **JDK 11+**（AGP 7.1.2 要求）；本机通过 `gradle.properties` 指定 `org.gradle.java.home=E:\\jdk17`
- Android SDK **Platform 34**
- 后端需先启动：`flz_chat_business:8087`、`flz_chat` WS `8071`（参见 `CLIENT_API.md` §10）

### 7.3 联调步骤建议

1. 模拟器安装 APK，注册/登录账号；
2. 主页顶部应显示「实时通道已就绪」（`auth_ok` 后）；
3. 好友 → 搜索用户 → 发申请 → 同意 → 进入单聊 → WS 发文本；
4. 社交 Tab 发布动态并点赞；
5. 智能体 Tab 提问「如何发送文本消息」验证规则回复。

---

## 8. 已知限制（课设范围）

- 未实现媒体消息上传（`presign` + `POST /api/messages`），仅文本走 WS；
- 群聊管理、撤回、动态图片等未做完整 UI；
- Agent 为本地规则引擎，非真实大模型；
- `gradle.properties` 中 JDK 路径为开发机路径，其他机器需自行修改；
- Room 使用 `fallbackToDestructiveMigration`，升级 schema 会清空本地库。

---

## 9. 本次变更统计（概要）

- 自空工程扩展为 **完整四 Tab 客户端**；
- 新增 Java 源文件约 **45+**，布局与资源 **20+**；
- 更新 `app/build.gradle` 依赖与 `AndroidManifest` 权限/Activity 注册；
- 新增 `docs/EXECUTION_REPORT.md`（本文档）。

---

## 10. 结论

已按两份客户端文档完成 **课设水准** 的 FLZ Chat Android 应用：登录门禁、HTTP+WebSocket 双通道、SQLite 缓存、四主导航与本地智能体助手，并通过 `assembleDebug` 编译验证。后续可在真机修改 `BuildConfig` 地址、对接真实 Agent 服务或补充媒体消息流程以扩展功能。
