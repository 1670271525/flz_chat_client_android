package com.flz.flz_chat.data.repository;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.data.local.AppDatabase;
import com.flz.flz_chat.data.local.entity.FriendEntity;
import com.flz.flz_chat.data.remote.ApiService;
import com.flz.flz_chat.data.remote.RetrofitClient;
import com.flz.flz_chat.data.remote.dto.ChatDtos;
import com.flz.flz_chat.util.ApiCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FriendRepository {

    private final AppDatabase db = FlzChatApp.get().getDatabase();
    private final ApiService api = RetrofitClient.getApi();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private long ownerUserId() {
        return FlzChatApp.get().getSessionManager().getUserId();
    }

    public void syncFriends(Runnable onDone) {
        api.getFriends(1, 50).enqueue(new ApiCallback<com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.FriendItem>>() {
            @Override
            public void onSuccess(com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.FriendItem> data) {
                io.execute(() -> {
                    if (data != null && data.records != null) {
                        long ownerUserId = ownerUserId();
                        List<FriendEntity> list = new ArrayList<>();
                        for (ChatDtos.FriendItem f : data.records) {
                            FriendEntity e = new FriendEntity();
                            e.ownerUserId = ownerUserId;
                            e.userId = f.userId;
                            e.alias = f.alias;
                            e.nickname = f.nickname;
                            e.avatarUrl = f.avatarUrl;
                            e.signature = f.signature;
                            list.add(e);
                        }
                        db.friendDao().upsertAll(list);
                    }
                    if (onDone != null) onDone.run();
                });
            }

            @Override
            public void onError(String message) {
                if (onDone != null) onDone.run();
            }
        });
    }

    public List<FriendEntity> getLocalFriends() {
        return db.friendDao().getAll(ownerUserId());
    }

    public void sendRequest(long toUserId, String remark, ApiCallback<Void> cb) {
        api.sendFriendRequest(new ChatDtos.FriendRequestBody(toUserId, remark)).enqueue(cb);
    }

    public void loadIncoming(ApiCallback<List<ChatDtos.FriendRequestItem>> cb) {
        loadIncomingRequests(0, cb);
    }

    public void countPendingIncoming(ApiCallback<Integer> cb) {
        loadIncomingRequests(0, new ApiCallback<List<ChatDtos.FriendRequestItem>>() {
            @Override
            public void onSuccess(List<ChatDtos.FriendRequestItem> data) {
                cb.onSuccess(data != null ? data.size() : 0);
            }

            @Override
            public void onError(String message) {
                cb.onSuccess(0);
            }
        });
    }

    private void loadIncomingRequests(int status, ApiCallback<List<ChatDtos.FriendRequestItem>> cb) {
        api.getIncomingRequests(status, 1, 20).enqueue(new ApiCallback<com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.FriendRequestItem>>() {
            @Override
            public void onSuccess(com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.FriendRequestItem> data) {
                List<ChatDtos.FriendRequestItem> list = new ArrayList<>();
                if (data != null && data.records != null) {
                    for (ChatDtos.FriendRequestItem item : data.records) {
                        if (item != null && item.resolvedRequestId() > 0) {
                            list.add(item);
                        }
                    }
                }
                cb.onSuccess(list);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void accept(long requestId, ApiCallback<Long> cb) {
        if (requestId <= 0) {
            cb.onError("无效的申请 ID");
            return;
        }
        api.acceptRequest(requestId).enqueue(new ApiCallback<ChatDtos.ConversationIdResponse>() {
            @Override
            public void onSuccess(ChatDtos.ConversationIdResponse data) {
                if (data == null || data.conversationId <= 0) {
                    cb.onError("无会话");
                    return;
                }
                syncFriends(() -> cb.onSuccess(data.conversationId));
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void reject(long requestId, ApiCallback<Void> cb) {
        if (requestId <= 0) {
            cb.onError("无效的申请 ID");
            return;
        }
        api.rejectRequest(requestId).enqueue(cb);
    }

    public void block(long friendId, ApiCallback<Void> cb) {
        api.blockFriend(friendId).enqueue(cb);
    }

    public void unblock(long friendId, ApiCallback<Void> cb) {
        api.unblockFriend(friendId).enqueue(cb);
    }

    public void delete(long friendId, ApiCallback<Void> cb) {
        api.deleteFriend(friendId).enqueue(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                io.execute(() -> {
                    long owner = ownerUserId();
                    List<FriendEntity> all = db.friendDao().getAll(owner);
                    List<FriendEntity> kept = new ArrayList<>();
                    for (FriendEntity f : all) {
                        if (f.userId != friendId) {
                            kept.add(f);
                        }
                    }
                    db.friendDao().deleteAllForOwner(owner);
                    if (!kept.isEmpty()) {
                        db.friendDao().upsertAll(kept);
                    }
                });
                cb.onSuccess(null);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void clearLocalCacheForOwner(long ownerUserId) {
        db.friendDao().deleteAllForOwner(ownerUserId);
    }
}
