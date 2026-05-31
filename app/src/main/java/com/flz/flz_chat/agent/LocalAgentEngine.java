package com.flz.flz_chat.agent;

import java.util.Locale;

/**
 * 课设级本地智能体：基于关键词的规则回复，数据存 SQLite。
 * 生产环境可替换为对接 LLM/OpenAPI 的实现。
 */
public final class LocalAgentEngine {

    private LocalAgentEngine() {}

    public static String reply(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "请描述你的问题，例如：如何发送文本消息？";
        }
        String q = userMessage.toLowerCase(Locale.ROOT);

        if (containsAny(q, "登录", "注册", "token", "鉴权")) {
            return "登录流程：先调用业务服务 POST /api/auth/login 获取 token，再携带 token 连接 ws://host/flz/chat。"
                    + "未登录无法使用聊天、好友与动态功能。";
        }
        if (containsAny(q, "文本", "消息", "发送", "ws", "长连接", "websocket")) {
            return "纯文本消息请走 Chat 长连接的 msg.send（需 clientMsgId），不要调用 POST /api/messages。"
                    + "媒体消息才走 HTTP 上传 + POST /api/messages。";
        }
        if (containsAny(q, "好友", "申请", "friend")) {
            return "好友：POST /api/friends/requests 发起申请，对方同意后返回 conversationId 可进入单聊。"
                    + "在线时还会收到 WS 的 friend.request / friend.accept 推送。";
        }
        if (containsAny(q, "会话", "conversation", "群")) {
            return "会话列表用 GET /api/conversations；进入会话后拉 GET /api/messages 历史，"
                    + "离开时用 PUT /api/conversations/{id}/read 推进已读。";
        }
        if (containsAny(q, "动态", "社交", "social", "点赞")) {
            return "社交动态：GET /api/social/feed 浏览好友圈，POST /api/social 发布，点赞用 POST/DELETE like 接口。";
        }
        if (containsAny(q, "心跳", "ping", "断线", "重连")) {
            return "建议每 25 秒发送 ping；75 秒无活动服务端会断连。重连后等待 auth_ok 再发业务帧，"
                    + "并使用指数退避重连策略。";
        }
        if (containsAny(q, "存储", "sqlite", "缓存", "离线")) {
            return "本 App 使用 Room(SQLite) 缓存会话、消息、好友与动态；消息按 messageId 去重，"
                    + "clientMsgId 用于发送幂等。";
        }
        return "我已记录你的问题。你可以询问：登录鉴权、文本消息/长连接、好友与会话、社交动态、心跳重连或本地存储。";
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
