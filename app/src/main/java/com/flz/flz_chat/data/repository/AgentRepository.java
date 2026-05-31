package com.flz.flz_chat.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.flz.flz_chat.BuildConfig;
import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.data.local.AppDatabase;
import com.flz.flz_chat.data.local.entity.AgentMessageEntity;
import com.flz.flz_chat.data.local.entity.AgentSessionEntity;
import com.flz.flz_chat.agent.LocalAgentEngine;
import com.flz.flz_chat.data.remote.agent.AgentSseClient;
import com.flz.flz_chat.session.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 智能体仓库：对接 flz_agent SSE，本地 SQLite 缓存消息；同一会话串行请求。
 */
public class AgentRepository {

    public interface StreamListener {
        void onSessionReady(long sessionId, String title);
        void onMessagesChanged();
        void onStreamingDelta(long assistantMsgId, String fullText);
        void onToolStatus(String hint);
        void onFinished();
        void onError(String message);
    }

    private final AppDatabase db = FlzChatApp.get().getDatabase();
    private final SessionManager session = FlzChatApp.get().getSessionManager();
    private final AgentSseClient sseClient = new AgentSseClient();
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** 按本地 sessionId 串行，避免上下文错乱 */
    private final Map<Long, Object> sessionLocks = new ConcurrentHashMap<>();

    private long ownerUserId() {
        return session.getUserId();
    }

    public List<AgentSessionEntity> getSessions() {
        return db.agentDao().getSessions(ownerUserId());
    }

    public List<AgentMessageEntity> getMessages(long sessionId) {
        if (sessionId <= 0) {
            return new ArrayList<>();
        }
        return db.agentDao().getMessages(ownerUserId(), sessionId);
    }

    public AgentSessionEntity getSession(long sessionId) {
        if (sessionId <= 0) {
            return null;
        }
        return db.agentDao().getSession(ownerUserId(), sessionId);
    }

    public void updateAgentType(long localSessionId, String agentType) {
        if (localSessionId <= 0) return;
        long ownerUserId = ownerUserId();
        AgentSessionEntity s = db.agentDao().getSession(ownerUserId, localSessionId);
        if (s != null) {
            db.agentDao().updateSession(ownerUserId, localSessionId, s.title, System.currentTimeMillis(), safeAgentType(agentType));
        }
    }

    public boolean checkHealth() {
        return sseClient.healthCheck(BuildConfig.AGENT_BASE_URL);
    }

    public void sendMessageCreatingSessionIfNeeded(long localSessionId, String pendingAgentType,
                                                   String text, StreamListener listener) {
        String content = text != null ? text.trim() : "";
        if (content.isEmpty()) {
            notifyError(listener, "请输入消息");
            return;
        }
        io.execute(() -> {
            long lockKey = localSessionId > 0 ? localSessionId : (Long.MIN_VALUE + ownerUserId());
            Object lock = sessionLocks.computeIfAbsent(lockKey, k -> new Object());
            synchronized (lock) {
                doSend(localSessionId, pendingAgentType, content, listener);
            }
        });
    }

    private void doSend(long localSessionId, String pendingAgentType, String text, StreamListener listener) {
        long ownerUserId = ownerUserId();
        long now = System.currentTimeMillis();
        long resolvedSessionId = localSessionId;
        AgentSessionEntity sessionEntity;

        if (resolvedSessionId <= 0) {
            sessionEntity = new AgentSessionEntity();
            sessionEntity.ownerUserId = ownerUserId;
            sessionEntity.title = buildSessionTitle(text);
            sessionEntity.remoteSessionId = newRemoteSessionId();
            sessionEntity.agentType = safeAgentType(pendingAgentType);
            sessionEntity.updatedAt = now;
            resolvedSessionId = db.agentDao().insertSession(sessionEntity);
        } else {
            sessionEntity = db.agentDao().getSession(ownerUserId, resolvedSessionId);
            if (sessionEntity == null) {
                notifyError(listener, "会话不存在");
                return;
            }
            if (pendingAgentType != null && !pendingAgentType.trim().isEmpty()) {
                sessionEntity.agentType = pendingAgentType.trim();
            }
        }

        if (sessionEntity.remoteSessionId == null || sessionEntity.remoteSessionId.isEmpty()) {
            sessionEntity.remoteSessionId = newRemoteSessionId();
            db.agentDao().setRemoteSessionId(ownerUserId, resolvedSessionId, sessionEntity.remoteSessionId);
        }

        String title = sessionEntity.title;
        if (title == null || title.trim().isEmpty() || "新对话".equals(title.trim())) {
            title = buildSessionTitle(text);
        }
        String agentType = safeAgentType(sessionEntity.agentType);
        db.agentDao().updateSession(ownerUserId, resolvedSessionId, title, now, agentType);

        long finalResolvedSessionId = resolvedSessionId;
        String finalTitle = title;
        notifyUi(listener, () -> listener.onSessionReady(finalResolvedSessionId, finalTitle));

        AgentMessageEntity user = new AgentMessageEntity();
        user.ownerUserId = ownerUserId;
        user.sessionId = resolvedSessionId;
        user.role = "user";
        user.content = text;
        user.createdAt = now;
        user.status = "sent";
        db.agentDao().insertMessage(user);

        AgentMessageEntity assistant = new AgentMessageEntity();
        assistant.ownerUserId = ownerUserId;
        assistant.sessionId = resolvedSessionId;
        assistant.role = "assistant";
        assistant.content = "";
        assistant.createdAt = now + 1;
        assistant.status = "streaming";
        final long assistantId = db.agentDao().insertMessage(assistant);

        notifyUi(listener, () -> {
            listener.onMessagesChanged();
            listener.onStreamingDelta(assistantId, "");
        });

        StringBuilder full = new StringBuilder();
        String token = session.getToken();

        sseClient.chat(
                BuildConfig.AGENT_BASE_URL,
                token,
                sessionEntity.remoteSessionId,
                text,
                agentType,
                new AgentSseClient.Callback() {
                    @Override
                    public void onDelta(String delta) {
                        full.append(delta);
                        db.agentDao().updateMessage(ownerUserId, assistantId, full.toString(), "streaming");
                        String snapshot = full.toString();
                        notifyUi(listener, () -> listener.onStreamingDelta(assistantId, snapshot));
                    }

                    @Override
                    public void onToolCall(String name, String argsSummary) {
                        String hint = "调用工具: " + name;
                        notifyUi(listener, () -> listener.onToolStatus(hint));
                    }

                    @Override
                    public void onToolResult(String name, boolean ok) {
                        String hint = ok ? ("工具完成: " + name) : ("工具失败: " + name);
                        notifyUi(listener, () -> listener.onToolStatus(hint));
                    }

                    @Override
                    public void onDone(String finishReason, int totalTokens) {
                        String finalText = full.length() > 0 ? full.toString() : "(无文本回复)";
                        db.agentDao().updateMessage(ownerUserId, assistantId, finalText, "sent");
                        notifyUi(listener, () -> {
                            listener.onMessagesChanged();
                            listener.onFinished();
                        });
                    }

                    @Override
                    public void onError(int code, String msg) {
                        String err = "[" + code + "] " + msg;
                        db.agentDao().updateMessage(ownerUserId, assistantId, err, "error");
                        notifyError(listener, err);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // 服务不可达时降级为本地规则回复（课设兜底）
                        String fallback = LocalAgentEngine.reply(text);
                        db.agentDao().updateMessage(ownerUserId, assistantId,
                                fallback + "\n\n(提示: flz_agent 未连接，以上为离线说明)", "sent");
                        notifyUi(listener, () -> {
                            listener.onMessagesChanged();
                            listener.onFinished();
                        });
                    }
                });
    }

    public void clearLocalCacheForOwner(long ownerUserId) {
        db.agentDao().deleteMessagesForOwner(ownerUserId);
        db.agentDao().deleteSessionsForOwner(ownerUserId);
    }

    private void notifyUi(StreamListener listener, Runnable r) {
        if (listener == null) return;
        main.post(r);
    }

    private void notifyError(StreamListener listener, String msg) {
        notifyUi(listener, () -> {
            listener.onMessagesChanged();
            listener.onError(msg);
            listener.onFinished();
        });
    }

    private static String safeAgentType(String agentType) {
        return (agentType == null || agentType.trim().isEmpty()) ? "chat" : agentType.trim();
    }

    private static String buildSessionTitle(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "新对话";
        }
        String safe = text.trim();
        return safe.length() > 16 ? safe.substring(0, 16) + "…" : safe;
    }

    private static String newRemoteSessionId() {
        return "chat_" + UUID.randomUUID().toString().replace("-", "");
    }
}
