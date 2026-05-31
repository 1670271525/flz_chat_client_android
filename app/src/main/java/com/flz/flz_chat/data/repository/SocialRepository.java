package com.flz.flz_chat.data.repository;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.data.local.AppDatabase;
import com.flz.flz_chat.data.local.entity.SocialEntity;
import com.flz.flz_chat.data.remote.ApiService;
import com.flz.flz_chat.data.remote.RetrofitClient;
import com.flz.flz_chat.data.remote.dto.ChatDtos;
import com.flz.flz_chat.data.remote.dto.UserDtos;
import com.flz.flz_chat.util.ApiCallback;
import com.flz.flz_chat.util.TimeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SocialRepository {

    private final AppDatabase db = FlzChatApp.get().getDatabase();
    private final ApiService api = RetrofitClient.getApi();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile String cachedSelfAvatarUrl;

    private long ownerUserId() {
        return FlzChatApp.get().getSessionManager().getUserId();
    }

    public void syncFeed(Runnable onDone) {
        refreshSelfAvatar();
        AtomicInteger pending = new AtomicInteger(2);
        Runnable finish = () -> {
            if (pending.decrementAndGet() == 0) {
                io.execute(() -> db.socialDao().deleteInvalid(ownerUserId()));
                if (onDone != null) onDone.run();
            }
        };

        api.getSocialFeed(1, 20).enqueue(new ApiCallback<com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.SocialPostItem>>() {
            @Override
            public void onSuccess(com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.SocialPostItem> data) {
                io.execute(() -> {
                    upsertPosts(data);
                    finish.run();
                });
            }

            @Override
            public void onError(String message) {
                finish.run();
            }
        });

        long myId = ownerUserId();
        if (myId > 0) {
            api.getUserSocial(myId, 1, 20).enqueue(new ApiCallback<com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.SocialPostItem>>() {
                @Override
                public void onSuccess(com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.SocialPostItem> data) {
                    io.execute(() -> {
                        upsertPosts(data);
                        finish.run();
                    });
                }

                @Override
                public void onError(String message) {
                    finish.run();
                }
            });
        } else {
            finish.run();
        }
    }

    private void refreshSelfAvatar() {
        api.getMe().enqueue(new ApiCallback<UserDtos.UserMe>() {
            @Override
            public void onSuccess(UserDtos.UserMe data) {
                if (data != null && data.information != null) {
                    cachedSelfAvatarUrl = data.information.avatarUrl;
                }
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void upsertPosts(com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.SocialPostItem> data) {
        if (data == null || data.records == null) {
            return;
        }
        long ownerUserId = ownerUserId();
        List<SocialEntity> list = new ArrayList<>();
        for (ChatDtos.SocialPostItem p : data.records) {
            SocialEntity e = mapPost(ownerUserId, p);
            if (e != null) {
                mergeLikeState(ownerUserId, e);
                list.add(e);
            }
        }
        if (!list.isEmpty()) {
            db.socialDao().upsertAll(list);
        }
    }

    public List<SocialEntity> getLocalFeed() {
        List<SocialEntity> list = db.socialDao().getAll(ownerUserId());
        List<SocialEntity> filtered = new ArrayList<>();
        for (SocialEntity e : list) {
            if (isValidPost(e)) {
                mergeLikeState(e.ownerUserId, e);
                enrichAvatar(e);
                filtered.add(e);
            }
        }
        Collections.sort(filtered, (a, b) -> Long.compare(
                TimeUtil.parseToMillis(b.createdAt),
                TimeUtil.parseToMillis(a.createdAt)));
        return filtered;
    }

    private void mergeLikeState(long ownerUserId, SocialEntity e) {
        com.flz.flz_chat.session.SessionManager session = FlzChatApp.get().getSessionManager();
        if (session.isSocialLiked(e.socialId)) {
            e.liked = true;
        }
        SocialEntity existing = db.socialDao().findBySocialId(ownerUserId, e.socialId);
        if (existing != null && existing.liked && !e.liked && session.isSocialLiked(e.socialId)) {
            e.liked = true;
            e.likeCount = Math.max(e.likeCount, existing.likeCount);
        }
        if (e.liked) {
            session.setSocialLiked(e.socialId, true);
        }
    }

    private void enrichAvatar(SocialEntity e) {
        if (e.avatarUrl != null && !e.avatarUrl.trim().isEmpty()) {
            return;
        }
        String fromFriend = db.friendDao().getAvatarUrl(e.ownerUserId, e.userId);
        if (fromFriend != null && !fromFriend.trim().isEmpty()) {
            e.avatarUrl = fromFriend;
            return;
        }
        if (e.userId == e.ownerUserId && cachedSelfAvatarUrl != null) {
            e.avatarUrl = cachedSelfAvatarUrl;
        }
    }

    public int countPostsSince(long sinceMillis) {
        long ownerId = ownerUserId();
        int count = 0;
        for (SocialEntity e : getLocalFeed()) {
            if (e.userId != ownerId && TimeUtil.parseToMillis(e.createdAt) > sinceMillis) {
                count++;
            }
        }
        return count;
    }

    /** 按已浏览的最大 socialId 统计未读，避免时间戳时区导致角标反复出现 */
    public int countUnseenPosts() {
        long ownerId = ownerUserId();
        long lastSeenId = FlzChatApp.get().getSessionManager().getLastSeenSocialId();
        int count = 0;
        for (SocialEntity e : getLocalFeed()) {
            if (e.userId != ownerId && e.socialId > lastSeenId) {
                count++;
            }
        }
        return count;
    }

    public void markFeedSeen() {
        markFeedSeen(null);
    }

    /** 访问 Room 须在后台线程调用 */
    public void markFeedSeen(Runnable onDone) {
        io.execute(() -> {
            long maxId = 0;
            for (SocialEntity e : getLocalFeed()) {
                if (e.socialId > maxId) {
                    maxId = e.socialId;
                }
            }
            FlzChatApp.get().getSessionManager().markSocialSeen(maxId);
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    public void post(String content, int visibility, ApiCallback<Void> cb) {
        api.postSocial(new ChatDtos.PostSocialRequest(content, visibility))
                .enqueue(new ApiCallback<ChatDtos.SocialPostItem>() {
                    @Override
                    public void onSuccess(ChatDtos.SocialPostItem data) {
                        if (data != null) {
                            io.execute(() -> {
                                SocialEntity e = mapPost(ownerUserId(), data);
                                if (e != null) {
                                    enrichAvatar(e);
                                    db.socialDao().upsert(e);
                                }
                            });
                        }
                        cb.onSuccess(null);
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    public void toggleLike(SocialEntity post, ApiCallback<SocialEntity> cb) {
        if (post.liked) {
            api.unlikeSocial(post.socialId).enqueue(wrapLike(post, false, cb));
        } else {
            api.likeSocial(post.socialId).enqueue(wrapLike(post, true, cb));
        }
    }

    private ApiCallback<Void> wrapLike(SocialEntity post, boolean liked, ApiCallback<SocialEntity> cb) {
        return new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                int count = liked ? post.likeCount + 1 : Math.max(0, post.likeCount - 1);
                post.liked = liked;
                post.likeCount = count;
                FlzChatApp.get().getSessionManager().setSocialLiked(post.socialId, liked);
                io.execute(() -> db.socialDao().updateLike(ownerUserId(), post.socialId, liked, count));
                cb.onSuccess(post);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        };
    }

    public void clearLocalCacheForOwner(long ownerUserId) {
        db.socialDao().deleteAllForOwner(ownerUserId);
    }

    private SocialEntity mapPost(long ownerUserId, ChatDtos.SocialPostItem p) {
        if (p == null || p.socialId <= 0) {
            return null;
        }
        long userId = p.userId;
        String nickname = p.nickname != null ? p.nickname : p.userName;
        String avatarUrl = p.avatarUrl;
        if (p.author != null) {
            if (userId <= 0) {
                userId = p.author.userId;
            }
            if (nickname == null || nickname.trim().isEmpty()) {
                nickname = p.author.nickname != null ? p.author.nickname : p.author.userName;
            }
            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                avatarUrl = p.author.avatarUrl;
            }
        }
        if (userId <= 0) {
            return null;
        }
        String content = p.content != null ? p.content.trim() : "";
        if (content.isEmpty() && (p.images == null || p.images.isEmpty())) {
            return null;
        }
        SocialEntity e = new SocialEntity();
        e.ownerUserId = ownerUserId;
        e.socialId = p.socialId;
        e.userId = userId;
        e.nickname = nickname;
        e.avatarUrl = avatarUrl;
        e.content = p.content;
        e.createdAt = p.createdAt;
        e.likeCount = p.likeCount;
        e.liked = p.liked;
        return e;
    }

    private static boolean isValidPost(SocialEntity e) {
        return e != null && e.socialId > 0 && e.userId > 0;
    }
}
