package com.flz.flz_chat.ui.chat;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.BuildConfig;
import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.MessageEntity;
import com.flz.flz_chat.data.remote.WsChatManager;
import com.flz.flz_chat.data.remote.agent.AgentSseClient;
import com.flz.flz_chat.data.repository.ChatRepository;
import com.flz.flz_chat.data.repository.FileRepository;
import com.flz.flz_chat.data.repository.UserRepository;
import com.flz.flz_chat.data.remote.dto.UserDtos;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * 单会话聊天：历史消息走 HTTP 落库，发送文本走 WS msg.send，图片走 HTTP 上传+发送。
 */
public class ChatActivity extends AppCompatActivity implements WsChatManager.Listener {

    public static final String EXTRA_CONVERSATION_ID = "conversationId";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PEER_AVATAR = "peerAvatarUrl";

    private final ChatRepository repo = new ChatRepository();
    private final FileRepository fileRepo = new FileRepository();
    private final UserRepository userRepo = new UserRepository();
    private long conversationId;
    private MessageAdapter adapter;
    private String selfAvatarUrl;
    private String peerAvatarUrl;
    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String> pickVideoLauncher;
    private ActivityResultLauncher<Uri> takePhotoLauncher;
    private MaterialButton btnSend;
    private MaterialButton btnImage;
    private MaterialButton btnMore;
    private boolean sendingImage;
    private boolean streamingEmotionReply;
    private Uri pendingCameraUri;
    private final AgentSseClient agentSseClient = new AgentSseClient();
    private static final Gson GSON = new Gson();
    private static final int MAX_AGENT_MSG_BYTES = 200 * 1024;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireLogin(this)) return;
        setContentView(R.layout.activity_chat);

        conversationId = getIntent().getLongExtra(EXTRA_CONVERSATION_ID, 0);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        peerAvatarUrl = getIntent().getStringExtra(EXTRA_PEER_AVATAR);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recyclerMessages);
        EditText etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnImage = findViewById(R.id.btnImage);
        btnMore = findViewById(R.id.btnMore);

        adapter = new MessageAdapter();
        adapter.setMyUserId(FlzChatApp.get().getSessionManager().getUserId());
        adapter.setAvatarProvider(new MessageAdapter.AvatarProvider() {
            @Override
            public String selfAvatarUrl() {
                return selfAvatarUrl;
            }

            @Override
            public String peerAvatarUrl() {
                return peerAvatarUrl;
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recycler.setLayoutManager(layoutManager);
        recycler.setAdapter(adapter);

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onImagePicked);

        pickVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onVideoPicked);

        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                ok -> {
                    Uri uri = pendingCameraUri;
                    pendingCameraUri = null;
                    if (Boolean.TRUE.equals(ok) && uri != null) {
                        onImagePicked(uri);
                    }
                });

        userRepo.fetchMe(new ApiCallback<UserDtos.UserMe>() {
            @Override
            public void onSuccess(UserDtos.UserMe data) {
                if (data != null && data.information != null) {
                    selfAvatarUrl = data.information.avatarUrl;
                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                }
            }

            @Override
            public void onError(String message) {
            }
        });

        FlzChatApp.get().getWsChatManager().addListener(this);
        loadMessages();

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
            if (text.isEmpty()) return;
            String clientMsgId = UUID.randomUUID().toString();
            Executors.newSingleThreadExecutor().execute(() -> {
                repo.insertPendingSync(conversationId, text, clientMsgId);
                FlzChatApp.get().getWsChatManager().sendTextMessage(conversationId, text, clientMsgId);
                runOnUiThread(() -> {
                    etMessage.setText("");
                    loadMessages();
                });
            });
        });

        btnImage.setOnClickListener(v -> {
            if (sendingImage) return;
            pickImageLauncher.launch("image/*");
        });

        if (btnMore != null) {
            btnMore.setOnClickListener(v -> showMoreActions());
        }
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        sendingImage = true;
        setImageSendingState(true);
        String clientMsgId = UUID.randomUUID().toString();
        Executors.newSingleThreadExecutor().execute(() -> {
            repo.insertPendingSyncBlocking(conversationId, 2, "", clientMsgId);
            runOnUiThread(this::refreshLocalMessages);
        });
        String mime = resolveMimeType(uri, "image/jpeg");
        String ext;
        if (mime.contains("png")) ext = ".png";
        else if (mime.contains("webp")) ext = ".webp";
        else ext = ".jpg";
        fileRepo.uploadChatMedia(uri, "chat_" + System.currentTimeMillis() + ext, mime,
                new FileRepository.UploadCallback() {
            @Override
            public void onSuccess(String objectKey) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    repo.updatePendingContentBlocking(clientMsgId, objectKey);
                    runOnUiThread(ChatActivity.this::refreshLocalMessages);
                });
                repo.sendImageMessage(conversationId, objectKey, clientMsgId, new ApiCallback<Long>() {
                    @Override
                    public void onSuccess(Long data) {
                        runOnUiThread(() -> {
                            sendingImage = false;
                            setImageSendingState(false);
                            loadMessages();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            sendingImage = false;
                            setImageSendingState(false);
                            Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                            loadMessages();
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    sendingImage = false;
                    setImageSendingState(false);
                    Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                    loadMessages();
                });
            }
        });
    }

    private void onVideoPicked(Uri uri) {
        if (uri == null) return;
        if (sendingImage) return;
        sendingImage = true;
        setImageSendingState(true);
        String clientMsgId = UUID.randomUUID().toString();
        Executors.newSingleThreadExecutor().execute(() -> {
            repo.insertPendingSyncBlocking(conversationId, 4, "", clientMsgId);
            runOnUiThread(this::refreshLocalMessages);
        });

        String mime = resolveMimeType(uri, "video/mp4");
        String ext = mime != null && mime.contains("mp4") ? ".mp4" : ".bin";
        fileRepo.uploadChatMedia(uri, "chat_" + System.currentTimeMillis() + ext, mime,
                new FileRepository.UploadCallback() {
                    @Override
                    public void onSuccess(String objectKey) {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            repo.updatePendingContentBlocking(clientMsgId, objectKey);
                            runOnUiThread(ChatActivity.this::refreshLocalMessages);
                        });
                        repo.sendMediaMessage(conversationId, 4, objectKey, clientMsgId, "{\"size\":0}",
                                new ApiCallback<Long>() {
                                    @Override
                                    public void onSuccess(Long data) {
                                        runOnUiThread(() -> {
                                            sendingImage = false;
                                            setImageSendingState(false);
                                            loadMessages();
                                        });
                                    }

                                    @Override
                                    public void onError(String message) {
                                        runOnUiThread(() -> {
                                            sendingImage = false;
                                            setImageSendingState(false);
                                            Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                                            loadMessages();
                                        });
                                    }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            sendingImage = false;
                            setImageSendingState(false);
                            Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                            loadMessages();
                        });
                    }
                });
    }

    private void showMoreActions() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_chat_more, null);
        dialog.setContentView(view);

        MaterialButton takePhoto = view.findViewById(R.id.actionTakePhoto);
        MaterialButton pickImage = view.findViewById(R.id.actionPickImage);
        MaterialButton pickVideo = view.findViewById(R.id.actionPickVideo);
        MaterialButton emotionReply = view.findViewById(R.id.actionEmotionReply);

        if (takePhoto != null) {
            takePhoto.setOnClickListener(v -> {
                dialog.dismiss();
                launchTakePhoto();
            });
        }
        if (pickImage != null) {
            pickImage.setOnClickListener(v -> {
                dialog.dismiss();
                if (!sendingImage) pickImageLauncher.launch("image/*");
            });
        }
        if (pickVideo != null) {
            pickVideo.setOnClickListener(v -> {
                dialog.dismiss();
                if (!sendingImage) pickVideoLauncher.launch("video/*");
            });
        }
        if (emotionReply != null) {
            emotionReply.setOnClickListener(v -> {
                dialog.dismiss();
                if (!streamingEmotionReply) startEmotionReply();
            });
        }

        dialog.show();
    }

    private void launchTakePhoto() {
        if (sendingImage) return;
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            File file = new File(dir, "chat_" + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            pendingCameraUri = uri;
            takePhotoLauncher.launch(uri);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相机: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startEmotionReply() {
        streamingEmotionReply = true;
        Toast.makeText(this, "智能代答生成中…", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                long myId = FlzChatApp.get().getSessionManager().getUserId();
                List<MessageEntity> history = repo.getLocalMessages(conversationId);
                String msgJson = buildEmotionReplyMsg(history, myId);
                String token = FlzChatApp.get().getSessionManager().getToken();

                agentSseClient.chat(
                        BuildConfig.AGENT_BASE_URL,
                        token,
                        "ignore",
                        msgJson,
                        "chat",
                        new AgentSseClient.Callback() {
                            @Override
                            public void onDelta(String delta) {
                                if (delta == null || delta.trim().isEmpty()) return;
                                String content = delta.trim();
                                String clientMsgId = UUID.randomUUID().toString();
                                Executors.newSingleThreadExecutor().execute(() -> {
                                    repo.insertPendingSync(conversationId, 1, content, clientMsgId, true);
                                    FlzChatApp.get().getWsChatManager()
                                            .sendTextMessage(conversationId, content, clientMsgId);
                                });
                                runOnUiThread(ChatActivity.this::refreshLocalMessages);
                            }

                            @Override
                            public void onToolCall(String name, String argsSummary) {
                            }

                            @Override
                            public void onToolResult(String name, boolean ok) {
                            }

                            @Override
                            public void onDone(String finishReason, int totalTokens) {
                                streamingEmotionReply = false;
                                runOnUiThread(ChatActivity.this::loadMessages);
                            }

                            @Override
                            public void onError(int code, String msg) {
                                streamingEmotionReply = false;
                                runOnUiThread(() -> Toast.makeText(ChatActivity.this,
                                        "智能代答失败: [" + code + "] " + msg, Toast.LENGTH_SHORT).show());
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                streamingEmotionReply = false;
                                runOnUiThread(() -> Toast.makeText(ChatActivity.this,
                                        "智能代答网络异常: " + (t != null ? t.getMessage() : ""), Toast.LENGTH_SHORT).show());
                            }
                        });
            } catch (Exception e) {
                streamingEmotionReply = false;
                runOnUiThread(() -> Toast.makeText(ChatActivity.this,
                        "智能代答异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 构造 emotion_reply 的 msg 字段（外层仍由 AgentSseClient 包一层 session_id/agent_type/msg）。
     * 限制：UTF-8 字节数不超过 200k，超出时从更早的聊天记录开始丢弃。
     */
    private String buildEmotionReplyMsg(List<MessageEntity> messages, long myUserId) {
        List<MessageEntity> safe = messages != null ? messages : new ArrayList<>();

        String peerLast = "";
        for (int i = safe.size() - 1; i >= 0; i--) {
            MessageEntity m = safe.get(i);
            if (m != null && m.type == 1 && m.senderId != myUserId) {
                peerLast = m.content != null ? m.content : "";
                break;
            }
        }

        // 从最新向前回溯，尽量塞满但不超过 MAX_AGENT_MSG_BYTES
        List<MessageEntity> picked = new ArrayList<>();
        for (int i = safe.size() - 1; i >= 0; i--) {
            MessageEntity m = safe.get(i);
            if (m == null) continue;
            if (m.type != 1) continue; // 仅文本放入上下文，避免媒体/长链接污染
            picked.add(0, m);

            String candidate = buildEmotionReplyMsgInternal(picked, myUserId, peerLast);
            int bytes = candidate.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_AGENT_MSG_BYTES) {
                // 超出则移除最早加入的一条（picked[0]）
                if (!picked.isEmpty()) picked.remove(0);
                break;
            }
        }
        return buildEmotionReplyMsgInternal(picked, myUserId, peerLast);
    }

    private String buildEmotionReplyMsgInternal(List<MessageEntity> picked, long myUserId, String peerLast) {
        JsonObject root = new JsonObject();
        root.addProperty("task", "emotion_reply");
        root.addProperty("language", "zh-CN");
        root.addProperty("tone", "温柔体贴");
        JsonObject constraints = new JsonObject();
        constraints.addProperty("max_chars", 60);
        root.add("constraints", constraints);
        root.addProperty("intent", "安抚");

        JsonArray history = new JsonArray();
        if (picked != null) {
            for (MessageEntity m : picked) {
                if (m == null) continue;
                JsonObject item = new JsonObject();
                item.addProperty("role", m.senderId == myUserId ? "me" : "peer");
                item.addProperty("content", m.content != null ? m.content : "");
                history.add(item);
            }
        }
        root.add("chat_history", history);
        root.addProperty("peer_last", peerLast != null ? peerLast : "");
        return GSON.toJson(root);
    }

    private String resolveMimeType(Uri uri, String fallback) {
        try {
            ContentResolver resolver = getContentResolver();
            String t = resolver != null ? resolver.getType(uri) : null;
            if (t != null && !t.trim().isEmpty()) return t;
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void setImageSendingState(boolean sending) {
        if (btnImage != null) {
            btnImage.setEnabled(!sending);
            btnImage.setAlpha(sending ? 0.5f : 1f);
        }
        if (btnMore != null) {
            btnMore.setEnabled(!sending && !streamingEmotionReply);
            btnMore.setAlpha((sending || streamingEmotionReply) ? 0.5f : 1f);
        }
    }

    private void loadMessages() {
        repo.loadMessages(conversationId, () -> refreshLocalMessages());
    }

    private void refreshLocalMessages() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<MessageEntity> list = repo.getLocalMessages(conversationId);
            Long maxId = null;
            for (MessageEntity m : list) {
                if (m.messageId > 0 && (maxId == null || m.messageId > maxId)) {
                    maxId = m.messageId;
                }
            }
            if (maxId != null) {
                repo.markConversationRead(conversationId, maxId, null);
            }
            runOnUiThread(() -> {
                adapter.setData(list);
                RecyclerView rv = findViewById(R.id.recyclerMessages);
                if (adapter.getItemCount() > 0) {
                    rv.scrollToPosition(adapter.getItemCount() - 1);
                }
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        repo.markConversationReadUpToLatest(conversationId);
    }

    @Override
    protected void onDestroy() {
        FlzChatApp.get().getWsChatManager().removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onStateChanged(WsChatManager.State state) {
    }

    @Override
    public void onMessageEvent(MessageEntity entity) {
        if (entity != null && entity.conversationId == conversationId) {
            runOnUiThread(this::refreshLocalMessages);
        }
    }

    @Override
    public void onSendResult(String clientMsgId, int code, long messageId) {
        runOnUiThread(() -> {
            if (code != 200) {
                Toast.makeText(this, "发送失败 code=" + code, Toast.LENGTH_SHORT).show();
            } else if (messageId > 0) {
                repo.markConversationRead(conversationId, messageId, null);
            }
            loadMessages();
        });
    }

    @Override
    public void onKicked(String reason) {
    }
}
