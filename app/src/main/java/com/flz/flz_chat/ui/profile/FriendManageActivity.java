package com.flz.flz_chat.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.FriendEntity;
import com.flz.flz_chat.data.remote.dto.ChatDtos;
import com.flz.flz_chat.data.repository.ChatRepository;
import com.flz.flz_chat.data.repository.FriendRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.ui.chat.ChatActivity;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * 好友管理（兼容入口，主入口已迁移至底部好友 Tab）。
 */
public class FriendManageActivity extends AppCompatActivity {

    private final FriendRepository friendRepo = new FriendRepository();
    private final ChatRepository chatRepo = new ChatRepository();
    private FriendAdapter friendAdapter;
    private FriendRequestAdapter requestAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireLogin(this)) return;
        setContentView(R.layout.activity_friend_manage);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.friends);
        toolbar.setNavigationOnClickListener(v -> finish());

        MaterialButton btnSearch = findViewById(R.id.btnSearch);
        RecyclerView recyclerFriends = findViewById(R.id.recyclerFriends);
        RecyclerView recyclerRequests = findViewById(R.id.recyclerRequests);

        friendAdapter = new FriendAdapter(new FriendAdapter.Listener() {
            @Override
            public void onChat(FriendEntity friend) {
                chatRepo.createSingleChat(friend.userId, new ApiCallback<Long>() {
                    @Override
                    public void onSuccess(Long conversationId) {
                        runOnUiThread(() -> {
                            Intent i = new Intent(FriendManageActivity.this, ChatActivity.class);
                            i.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId);
                            i.putExtra(ChatActivity.EXTRA_TITLE,
                                    friend.alias != null ? friend.alias : friend.nickname);
                            startActivity(i);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(FriendManageActivity.this,
                                message, Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onDetail(FriendEntity friend) {
                Intent i = new Intent(FriendManageActivity.this, FriendDetailActivity.class);
                i.putExtra(FriendDetailActivity.EXTRA_USER_ID, friend.userId);
                i.putExtra(FriendDetailActivity.EXTRA_NICKNAME, friend.nickname);
                i.putExtra(FriendDetailActivity.EXTRA_ALIAS, friend.alias);
                i.putExtra(FriendDetailActivity.EXTRA_AVATAR, friend.avatarUrl);
                i.putExtra(FriendDetailActivity.EXTRA_SIGNATURE, friend.signature);
                startActivity(i);
            }
        });

        requestAdapter = new FriendRequestAdapter(new FriendRequestAdapter.Listener() {
            @Override
            public void onAccept(ChatDtos.FriendRequestItem item, Runnable onComplete) {
                friendRepo.accept(item.resolvedRequestId(), new ApiCallback<Long>() {
                    @Override
                    public void onSuccess(Long data) {
                        runOnUiThread(() -> {
                            Toast.makeText(FriendManageActivity.this,
                                    "已同意好友申请", Toast.LENGTH_SHORT).show();
                            if (data != null && data > 0) {
                                Intent i = new Intent(FriendManageActivity.this, ChatActivity.class);
                                i.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, data);
                                String title = item.fromNickname != null && !item.fromNickname.trim().isEmpty()
                                        ? item.fromNickname : String.valueOf(item.fromUserId);
                                i.putExtra(ChatActivity.EXTRA_TITLE, title);
                                startActivity(i);
                            }
                            loadData();
                            onComplete.run();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(FriendManageActivity.this,
                                    message, Toast.LENGTH_SHORT).show();
                            onComplete.run();
                        });
                    }
                });
            }

            @Override
            public void onReject(ChatDtos.FriendRequestItem item, Runnable onComplete) {
                friendRepo.reject(item.resolvedRequestId(), new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        runOnUiThread(() -> {
                            Toast.makeText(FriendManageActivity.this,
                                    "已拒绝好友申请", Toast.LENGTH_SHORT).show();
                            loadData();
                            onComplete.run();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(FriendManageActivity.this,
                                    message, Toast.LENGTH_SHORT).show();
                            onComplete.run();
                        });
                    }
                });
            }
        });

        recyclerFriends.setLayoutManager(new LinearLayoutManager(this));
        recyclerFriends.setAdapter(friendAdapter);
        recyclerRequests.setLayoutManager(new LinearLayoutManager(this));
        recyclerRequests.setAdapter(requestAdapter);

        btnSearch.setOnClickListener(v ->
                startActivity(new Intent(this, SearchUserActivity.class)));

        loadData();
    }

    private void loadData() {
        friendRepo.syncFriends(() -> Executors.newSingleThreadExecutor().execute(() -> {
            List<FriendEntity> friends = friendRepo.getLocalFriends();
            runOnUiThread(() -> friendAdapter.setData(friends));
        }));
        friendRepo.loadIncoming(new ApiCallback<List<ChatDtos.FriendRequestItem>>() {
            @Override
            public void onSuccess(List<ChatDtos.FriendRequestItem> data) {
                runOnUiThread(() -> requestAdapter.setData(data));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(FriendManageActivity.this,
                        message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }
}
