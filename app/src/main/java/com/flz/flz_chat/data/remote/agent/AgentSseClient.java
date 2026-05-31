package com.flz.flz_chat.data.remote.agent;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * flz_agent SSE 客户端：POST /v1/chat/sse，解析 message / tool_* / done / error 事件。
 * 协议见 docs/CLIENT_API_AGENT.md §6。
 */
public class AgentSseClient {

    private static final String TAG = "AgentSse";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    public interface Callback {
        /** 流式文本增量 */
        void onDelta(String delta);
        /** 工具调用（可选展示） */
        void onToolCall(String name, String argsSummary);
        void onToolResult(String name, boolean ok);
        /** 本轮完成 */
        void onDone(String finishReason, int totalTokens);
        /** 业务错误（HTTP 200 内 error 事件） */
        void onError(int code, String msg);
        /** 网络/解析异常 */
        void onFailure(Throwable t);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(130, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 同步阻塞调用，请在后台线程执行；同一 remoteSessionId 由调用方串行化。
     */
    public void chat(String baseUrl, String token, String remoteSessionId,
                     String message, String agentType, Callback callback) {
        String url = trimSlash(baseUrl) + "/v1/chat/sse";
        JsonObject body = new JsonObject();
        body.addProperty("session_id", remoteSessionId);
        body.addProperty("msg", message);
        body.addProperty("agent_type", agentType != null ? agentType : "chat");

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .addHeader("Accept", "text/event-stream")
                .addHeader("Connection", "close")
                .post(RequestBody.create(body.toString(), JSON));

        if (token != null && !token.isEmpty()) {
            reqBuilder.addHeader("Authorization", "Bearer " + token);
        }

        try (Response response = client.newCall(reqBuilder.build()).execute()) {
            if (response.body() == null) {
                callback.onFailure(new IllegalStateException("empty body"));
                return;
            }
            parseSseStream(new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8)), callback);
        } catch (Exception e) {
            Log.e(TAG, "sse request failed", e);
            callback.onFailure(e);
        }
    }

    /** 健康检查 GET /v1/health */
    public boolean healthCheck(String baseUrl) {
        try {
            Request req = new Request.Builder()
                    .url(trimSlash(baseUrl) + "/v1/health")
                    .get()
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                return resp.isSuccessful() && resp.body() != null
                        && resp.body().string().contains("\"code\":0");
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void parseSseStream(BufferedReader reader, Callback callback) throws Exception {
        String currentEvent = "message";
        StringBuilder dataLines = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                dispatchEvent(currentEvent, dataLines.toString(), callback);
                dataLines.setLength(0);
                currentEvent = "message";
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("event:")) {
                currentEvent = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (dataLines.length() > 0) {
                    dataLines.append('\n');
                }
                dataLines.append(line.substring(5).trim());
            }
        }
        if (dataLines.length() > 0) {
            dispatchEvent(currentEvent, dataLines.toString(), callback);
        }
    }

    private void dispatchEvent(String event, String dataJson, Callback callback) {
        if (dataJson == null || dataJson.isEmpty()) {
            return;
        }
        try {
            JsonObject data = JsonParser.parseString(dataJson).getAsJsonObject();
            switch (event) {
                case "message":
                    if (data.has("delta")) {
                        callback.onDelta(data.get("delta").getAsString());
                    }
                    break;
                case "tool_call":
                    String name = data.has("name") ? data.get("name").getAsString() : "tool";
                    String args = data.has("args") ? data.get("args").toString() : "";
                    callback.onToolCall(name, args);
                    break;
                case "tool_result":
                    callback.onToolResult(
                            data.has("name") ? data.get("name").getAsString() : "tool",
                            !data.has("ok") || data.get("ok").getAsBoolean());
                    break;
                case "done":
                    int total = 0;
                    if (data.has("usage") && data.get("usage").isJsonObject()) {
                        JsonObject u = data.getAsJsonObject("usage");
                        if (u.has("total_tokens")) {
                            total = u.get("total_tokens").getAsInt();
                        }
                    }
                    String reason = data.has("finish_reason")
                            ? data.get("finish_reason").getAsString() : "stop";
                    callback.onDone(reason, total);
                    break;
                case "error":
                    int code = data.has("code") ? data.get("code").getAsInt() : 500;
                    String msg = data.has("msg") ? data.get("msg").getAsString() : "error";
                    callback.onError(code, msg);
                    break;
                default:
                    Log.d(TAG, "ignore event: " + event);
            }
        } catch (Exception e) {
            Log.w(TAG, "parse event " + event + " failed: " + dataJson, e);
        }
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
