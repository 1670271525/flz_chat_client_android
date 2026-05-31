package com.flz.flz_chat.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.annotation.Nullable;

/**
 * 持久化登录态：accessToken、refreshToken、userId 等。
 * 未登录时 token 为空，各业务入口应跳转登录页。
 */
public class SessionManager {

    private static final String PREF = "flz_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_SOCIAL_SEEN_AT = "social_seen_at";
    private static final String KEY_LAST_SEEN_SOCIAL_ID = "last_seen_social_id";
    private static final String KEY_CONV_READ_PREFIX = "conv_last_read_";
    private static final String KEY_LIKED_SOCIAL_IDS = "liked_social_ids";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public boolean isLoggedIn() {
        return getToken() != null && !getToken().isEmpty();
    }

    public void saveLogin(long userId, String userName, String token, String refreshToken) {
        prefs.edit()
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_USER_NAME, userName)
                .putString(KEY_TOKEN, token)
                .putString(KEY_REFRESH, refreshToken)
                .apply();
    }

    public void saveProfile(String nickname) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    @Nullable
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    @Nullable
    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH, null);
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, 0);
    }

    @Nullable
    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, null);
    }

    @Nullable
    public String getNickname() {
        return prefs.getString(KEY_NICKNAME, null);
    }

    public void updateToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public long getSocialSeenAt() {
        return prefs.getLong(KEY_SOCIAL_SEEN_AT, 0);
    }

    public long getLastSeenSocialId() {
        return prefs.getLong(KEY_LAST_SEEN_SOCIAL_ID, 0);
    }

    /** 同步写入，避免角标刷新读到旧值 */
    public void markSocialSeen(long maxSocialId) {
        prefs.edit()
                .putLong(KEY_SOCIAL_SEEN_AT, System.currentTimeMillis())
                .putLong(KEY_LAST_SEEN_SOCIAL_ID, Math.max(getLastSeenSocialId(), maxSocialId))
                .commit();
    }

    public void markSocialSeen() {
        markSocialSeen(getLastSeenSocialId());
    }

    public long getConversationLastRead(long conversationId) {
        return prefs.getLong(KEY_CONV_READ_PREFIX + conversationId, 0);
    }

    public void setConversationLastRead(long conversationId, long lastReadMessageId) {
        if (conversationId <= 0 || lastReadMessageId <= 0) {
            return;
        }
        long prev = getConversationLastRead(conversationId);
        if (lastReadMessageId > prev) {
            prefs.edit()
                    .putLong(KEY_CONV_READ_PREFIX + conversationId, lastReadMessageId)
                    .commit();
        }
    }

    public boolean isSocialLiked(long socialId) {
        if (socialId <= 0) {
            return false;
        }
        java.util.Set<String> ids = prefs.getStringSet(KEY_LIKED_SOCIAL_IDS, null);
        return ids != null && ids.contains(String.valueOf(socialId));
    }

    public void setSocialLiked(long socialId, boolean liked) {
        if (socialId <= 0) {
            return;
        }
        java.util.Set<String> current = prefs.getStringSet(KEY_LIKED_SOCIAL_IDS, null);
        java.util.Set<String> updated = new java.util.HashSet<>();
        if (current != null) {
            updated.addAll(current);
        }
        String key = String.valueOf(socialId);
        if (liked) {
            updated.add(key);
        } else {
            updated.remove(key);
        }
        prefs.edit().putStringSet(KEY_LIKED_SOCIAL_IDS, updated).apply();
    }

    /** JWT 中的 did：Android ID，用于长连接多端区分 */
    public String getDeviceId(Context context) {
        String cached = prefs.getString(KEY_DEVICE_ID, null);
        if (cached != null) {
            return cached;
        }
        String id = "android-" + Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        return id;
    }
}
