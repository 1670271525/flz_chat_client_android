package com.flz.flz_chat.util;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 时间格式化：社交相对时间、聊天气泡间隔时间。
 */
public final class TimeUtil {

    private static final SimpleDateFormat CHAT_GAP =
            new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());

    private TimeUtil() {
    }

    /** 60分钟内 xx分钟前，24小时内 xx小时前，否则 xx天前 */
    @Nullable
    public static String formatRelative(@Nullable String raw) {
        long millis = parseToMillis(raw);
        if (millis <= 0) {
            return raw != null ? raw : "";
        }
        long diffMs = Math.max(0, System.currentTimeMillis() - millis);
        long minutes = diffMs / 60_000L;
        if (minutes < 1) {
            return "刚刚";
        }
        if (minutes < 60) {
            return minutes + "分钟前";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "小时前";
        }
        long days = hours / 24;
        return days + "天前";
    }

    /** 聊天气泡间隔：月/日 时:分 */
    @Nullable
    public static String formatChatGap(long millis) {
        if (millis <= 0) {
            return null;
        }
        synchronized (CHAT_GAP) {
            return CHAT_GAP.format(new Date(millis));
        }
    }

    public static long parseToMillis(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        String value = raw.trim();
        if (value.matches("\\d+")) {
            try {
                long num = Long.parseLong(value);
                // 秒级时间戳兼容
                return num < 1_000_000_000_000L ? num * 1000L : num;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        if (value.endsWith("Z")) {
            return parseWithPattern(value, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"))
                    > 0 ? parseWithPattern(value, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"))
                    : parseWithPattern(value, "yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"));
        }
        if (value.contains("+") || (value.length() > 19 && value.lastIndexOf('-') > 10)) {
            return parseWithPattern(value, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", null)
                    > 0 ? parseWithPattern(value, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", null)
                    : parseWithPattern(value, "yyyy-MM-dd'T'HH:mm:ssXXX", null);
        }
        // 无时区后缀的 ISO 时间按 UTC 解析（服务端常见格式）
        if (value.contains("T")) {
            long utc = parseWithPattern(value, "yyyy-MM-dd'T'HH:mm:ss.SSS", TimeZone.getTimeZone("UTC"));
            if (utc > 0) return utc;
            utc = parseWithPattern(value, "yyyy-MM-dd'T'HH:mm:ss", TimeZone.getTimeZone("UTC"));
            if (utc > 0) return utc;
        }
        String[] localPatterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : localPatterns) {
            long ts = parseWithPattern(value, pattern, null);
            if (ts > 0) {
                return ts;
            }
        }
        return 0;
    }

    private static long parseWithPattern(String value, String pattern, @Nullable TimeZone tz) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
            if (tz != null) {
                sdf.setTimeZone(tz);
            }
            Date date = sdf.parse(value);
            return date != null ? date.getTime() : 0;
        } catch (ParseException ignored) {
            return 0;
        }
    }
}
