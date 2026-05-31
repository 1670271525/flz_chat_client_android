package com.flz.flz_chat.ui.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.MessageEntity;
import com.flz.flz_chat.data.remote.WsChatManager;
import com.flz.flz_chat.data.repository.ChatRepository;
import com.flz.flz_chat.data.repository.FileRepository;
import com.flz.flz_chat.data.repository.UserRepository;
import com.flz.flz_chat.data.remote.dto.UserDtos;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

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
    private MaterialButton btnSend;
    private MaterialButton btnImage;
    private boolean sendingImage;

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
        fileRepo.uploadChatMedia(uri, "chat_" + System.currentTimeMillis() + ".jpg", "image/jpeg",
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

    private void setImageSendingState(boolean sending) {
        if (btnImage != null) {
            btnImage.setEnabled(!sending);
            btnImage.setAlpha(sending ? 0.5f : 1f);
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
