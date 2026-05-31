package com.flz.flz_chat.util;

import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.repository.FileRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * 头像加载：支持 http URL 与 objectKey。
 */
public final class AvatarHelper {

    private static final Map<String, String> URL_CACHE = new HashMap<>();
    private static final FileRepository FILE_REPO = new FileRepository();

    private AvatarHelper() {
    }

    public static void load(ImageView target, @Nullable String avatarUrl) {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            target.setImageResource(R.drawable.bg_avatar_placeholder);
            return;
        }
        String key = avatarUrl.trim();
        if (key.startsWith("http")) {
            ImageLoader.load(target, key);
            return;
        }
        String cached = URL_CACHE.get(key);
        if (cached != null) {
            ImageLoader.load(target, cached);
            return;
        }
        target.setImageResource(R.drawable.bg_avatar_placeholder);
        FILE_REPO.resolveDownloadUrl(key, new ApiCallback<String>() {
            @Override
            public void onSuccess(String data) {
                URL_CACHE.put(key, data);
                ImageLoader.load(target, data);
            }

            @Override
            public void onError(String message) {
            }
        });
    }
}
