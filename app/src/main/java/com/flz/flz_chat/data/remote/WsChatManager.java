package com.flz.flz_chat.data.remote;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.flz.flz_chat.BuildConfig;
import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.data.local.entity.MessageEntity;
import com.flz.flz_chat.data.repository.ChatRepository;
import com.flz.flz_chat.session.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Chat 长连接客户端：鉴权、心跳、文本 msg.send、下行帧处理。
 * 协议见 docs/CLIENT_API.md。
 */
public class WsChatManager {

    private static final String TAG = "WsChat";
    private static final long HEARTBEAT_MS = 25_000;
    private static final long PONG_TIMEOUT_MS = 10_000;

    public interface Listener {
        void onStateChanged(State state);
        void onMessageEvent(MessageEntity entity);
        void onSendResult(String clientMsgId, int code, long messageId);
        void onKicked(String reason);

        /** ping 超时未收到 pong 时为 false，恢复后为 true */
        default void onHeartbeatChanged(boolean healthy) {
        }

        /** 好友/会话等推送，触发 UI 刷新 */
        default void onRealtimeEvent(String eventType) {
        }
    }

    public enum State { DISCONNECTED, CONNECTING, AUTHENTICATED }

    private final SessionManager session;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger seq = new AtomicInteger(0);
    private final List<Listener> listeners = new ArrayList<>();

    private WebSocket webSocket;
    private State state = State.DISCONNECTED;
    private Runnable heartbeatTask;
    private Runnable pongTimeoutTask;
    private boolean heartbeatHealthy = true;

    public WsChatManager(SessionManager session) {
        this.session = session;
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public State getState() {
        return state;
    }

    public boolean isHeartbeatHealthy() {
        return heartbeatHealthy;
    }

    public void connectIfLoggedIn() {
        if (!session.isLoggedIn()) {
            return;
        }
        if (state == State.CONNECTING || state == State.AUTHENTICATED) {
            return;
        }
        setState(State.CONNECTING);
        String token = session.getToken();
        String url = "ws://" + BuildConfig.WS_HOST + ":" + BuildConfig.WS_PORT
                + BuildConfig.WS_CHAT_PATH + "?token=" + token;

        OkHttpClient client = new OkHttpClient.Builder()
                .pingInterval(0, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, new SocketListener());
    }

    public void disconnect() {
        stopHeartbeat();
        if (webSocket != null) {
            try {
                sendFrame("bye", null, null);
            } catch (Exception ignored) {
            }
            webSocket.close(1000, "logout");
            webSocket = null;
        }
        setState(State.DISCONNECTED);
        setHeartbeatHealthy(true);
    }

    /** 通过 WS 发送文本消息（type=1） */
    public void sendTextMessage(long conversationId, String content, String clientMsgId) {
        JsonObject data = new JsonObject();
        data.addProperty("clientMsgId", clientMsgId);
        data.addProperty("conversationId", conversationId);
        data.addProperty("content", content);
        sendFrame("msg.send", data, clientMsgId);
    }

    public void sendAck(long messageId) {
        JsonObject data = new JsonObject();
        data.addProperty("messageId", messageId);
        sendFrame("msg.ack", data, null);
    }

    private void sendFrame(String type, JsonObject data, String trackClientMsgId) {
        if (webSocket == null || state != State.AUTHENTICATED) {
            return;
        }
        JsonObject frame = new JsonObject();
        frame.addProperty("type", type);
        frame.addProperty("seq", seq.incrementAndGet());
        frame.add("data", data != null ? data : new JsonObject());
        webSocket.send(frame.toString());
    }

    private void startHeartbeat() {
        stopHeartbeat();
        setHeartbeatHealthy(true);
        heartbeatTask = () -> {
            if (state == State.AUTHENTICATED && webSocket != null) {
                JsonObject data = new JsonObject();
                data.addProperty("ts", System.currentTimeMillis());
                sendFrame("ping", data, null);
                schedulePongTimeout();
                mainHandler.postDelayed(heartbeatTask, HEARTBEAT_MS);
            }
        };
        mainHandler.postDelayed(heartbeatTask, HEARTBEAT_MS);
    }

    private void schedulePongTimeout() {
        if (pongTimeoutTask != null) {
            mainHandler.removeCallbacks(pongTimeoutTask);
        }
        pongTimeoutTask = () -> {
            if (state == State.AUTHENTICATED) {
                setHeartbeatHealthy(false);
            }
        };
        mainHandler.postDelayed(pongTimeoutTask, PONG_TIMEOUT_MS);
    }

    private void onPongReceived() {
        if (pongTimeoutTask != null) {
            mainHandler.removeCallbacks(pongTimeoutTask);
            pongTimeoutTask = null;
        }
        setHeartbeatHealthy(true);
    }

    private void setHeartbeatHealthy(boolean healthy) {
        if (heartbeatHealthy == healthy) {
            return;
        }
        heartbeatHealthy = healthy;
        mainHandler.post(() -> {
            for (Listener l : listeners) {
                l.onHeartbeatChanged(healthy);
            }
        });
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            mainHandler.removeCallbacks(heartbeatTask);
            heartbeatTask = null;
        }
        if (pongTimeoutTask != null) {
            mainHandler.removeCallbacks(pongTimeoutTask);
            pongTimeoutTask = null;
        }
    }

    private void setState(State s) {
        state = s;
        mainHandler.post(() -> {
            for (Listener l : listeners) {
                l.onStateChanged(s);
            }
        });
    }

    private void handleFrame(String text) {
        try {
            JsonObject frame = JsonParser.parseString(text).getAsJsonObject();
            String type = frame.get("type").getAsString();
            JsonObject data = frame.has("data") && !frame.get("data").isJsonNull()
                    ? frame.get("data").getAsJsonObject() : new JsonObject();

            switch (type) {
                case "auth_ok":
                    setState(State.AUTHENTICATED);
                    startHeartbeat();
                    break;
                case "auth_fail":
                    setState(State.DISCONNECTED);
                    break;
                case "pong":
                    onPongReceived();
                    break;
                case "msg.send.resp":
                    notifySendResp(data);
                    break;
                case "msg.new":
                case "msg.replay":
                    persistIncoming(type, data);
                    break;
                case "msg.recall":
                    notifyRealtime(type);
                    break;
                case "friend.request":
                case "friend.accept":
                case "conversation.created":
                case "conversation.member_changed":
                    notifyRealtime(type);
                    break;
                case "kicked":
                    String reason = data.has("reason") ? data.get("reason").getAsString() : "kicked";
                    mainHandler.post(() -> {
                        for (Listener l : listeners) {
                            l.onKicked(reason);
                        }
                    });
                    disconnect();
                    break;
                case "error":
                    Log.w(TAG, "ws error: " + data);
                    break;
                default:
                    if (type != null && type.startsWith("social.")) {
                        notifyRealtime(type);
                    } else {
                        Log.d(TAG, "unknown frame: " + type);
                    }
            }
        } catch (Exception e) {
            Log.e(TAG, "parse frame failed", e);
        }
    }

    private void notifyRealtime(String eventType) {
        mainHandler.post(() -> {
            for (Listener l : listeners) {
                l.onRealtimeEvent(eventType);
            }
        });
    }

    private void notifySendResp(JsonObject data) {
        String clientMsgId = data.get("clientMsgId").getAsString();
        int code = data.get("code").getAsInt();
        long messageId = data.has("messageId") ? data.get("messageId").getAsLong() : 0;
        new Thread(() -> {
            ChatRepository repo = new ChatRepository();
            if (code == 200) {
                repo.confirmOutgoing(clientMsgId, messageId);
            } else {
                repo.failOutgoing(clientMsgId);
            }
        }).start();
        mainHandler.post(() -> {
            for (Listener l : listeners) {
                l.onSendResult(clientMsgId, code, messageId);
            }
        });
    }

    private void persistIncoming(String type, JsonObject data) {
        // Room 需在后台线程访问
        new Thread(() -> doPersistIncoming(type, data)).start();
    }

    private void doPersistIncoming(String type, JsonObject data) {
        ChatRepository repo = new ChatRepository();
        long myId = session.getUserId();
        if ("msg.new".equals(type)) {
            long senderId = data.get("senderId").getAsLong();
            String clientMsgId = null;
            if (data.has("clientMsgId") && !data.get("clientMsgId").isJsonNull()) {
                String raw = data.get("clientMsgId").getAsString();
                if (raw != null && !raw.isEmpty()) {
                    clientMsgId = raw;
                }
            }
            MessageEntity e = repo.saveIncomingFromWs(
                    data.get("messageId").getAsLong(),
                    data.get("conversationId").getAsLong(),
                    senderId,
                    data.get("type").getAsInt(),
                    data.get("content").getAsString(),
                    clientMsgId,
                    data.has("createdAt") ? data.get("createdAt").getAsString() : "",
                    myId,
                    senderId != myId
            );
            if (e != null) {
                mainHandler.post(() -> {
                    for (Listener l : listeners) {
                        l.onMessageEvent(e);
                    }
                });
            }
            if (data.get("senderId").getAsLong() != myId) {
                sendAck(data.get("messageId").getAsLong());
            }
        } else if ("msg.replay".equals(type) && data.has("messages")) {
            long cid = data.get("conversationId").getAsLong();
            for (com.google.gson.JsonElement el : data.getAsJsonArray("messages")) {
                JsonObject m = el.getAsJsonObject();
                long mid = m.has("message_id") ? m.get("message_id").getAsLong()
                        : m.get("messageId").getAsLong();
                long sid = m.has("sender_id") ? m.get("sender_id").getAsLong()
                        : m.get("senderId").getAsLong();
                int mtype = m.get("type").getAsInt();
                String content = m.get("content").getAsString();
                repo.saveIncomingFromWs(mid, cid, sid, mtype, content,
                        null, "", myId, false);
            }
        }
    }

    private class SocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.i(TAG, "ws opened");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            handleFrame(text);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            stopHeartbeat();
            setState(State.DISCONNECTED);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "ws failure", t);
            stopHeartbeat();
            setState(State.DISCONNECTED);
            // 简单退避重连
            mainHandler.postDelayed(() -> {
                if (FlzChatApp.get().getSessionManager().isLoggedIn()) {
                    state = State.DISCONNECTED;
                    connectIfLoggedIn();
                }
            }, 3000);
        }
    }
}
