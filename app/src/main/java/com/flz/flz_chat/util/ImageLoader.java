package com.flz.flz_chat.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.flz.flz_chat.R;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 简单网络图片加载（头像、聊天图片），无第三方依赖。
 */
public final class ImageLoader {

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Map<String, Bitmap> MEMORY = new ConcurrentHashMap<>();

    private ImageLoader() {
    }

    public static void load(ImageView target, @Nullable String url) {
        load(target, url, R.drawable.bg_avatar_placeholder);
    }

    public static void load(ImageView target, @Nullable String url, int placeholderRes) {
        target.setImageResource(placeholderRes);
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        String key = url.trim();
        Bitmap cached = MEMORY.get(key);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }
        target.setTag(key);
        IO.execute(() -> {
            Bitmap bitmap = fetchBitmap(key);
            MAIN.post(() -> {
                Object tag = target.getTag();
                if (tag == null || !key.equals(tag.toString())) {
                    return;
                }
                if (bitmap != null) {
                    target.setImageBitmap(bitmap);
                } else {
                    target.setImageResource(placeholderRes);
                }
            });
        });
    }

    @Nullable
    private static Bitmap fetchBitmap(String url) {
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                InputStream stream = response.body().byteStream();
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (bitmap != null) {
                    MEMORY.put(url, bitmap);
                }
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
