package com.flz.flz_chat.data.repository;

import android.content.ContentResolver;
import android.net.Uri;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.data.remote.ApiService;
import com.flz.flz_chat.data.remote.RetrofitClient;
import com.flz.flz_chat.data.remote.dto.FileDtos;
import com.flz.flz_chat.util.ApiCallback;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileRepository {

    public interface UploadCallback {
        void onSuccess(String objectKey);

        void onError(String message);
    }

    private final ApiService api = RetrofitClient.getApi();
    private final OkHttpClient http = new OkHttpClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** 公开桶：头像等图片，对应 flz-chat-public */
    public void uploadPublicImage(Uri uri, String filename, UploadCallback cb) {
        upload(uri, "public", filename, cb);
    }

    /** 聊天媒体桶，对应 flz-chat-media */
    public void uploadChatMedia(Uri uri, String filename, String contentType, UploadCallback cb) {
        upload(uri, "chat", filename, contentType, cb);
    }

    public void resolveDownloadUrl(String objectKey, ApiCallback<String> cb) {
        api.presignDownload(objectKey).enqueue(new ApiCallback<FileDtos.PresignDownloadResponse>() {
            @Override
            public void onSuccess(FileDtos.PresignDownloadResponse data) {
                if (data != null && data.url != null) {
                    cb.onSuccess(data.url);
                } else {
                    cb.onError("无法获取下载地址");
                }
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    private void upload(Uri uri, String bucket, String filename, UploadCallback cb) {
        upload(uri, bucket, filename, guessContentType(filename), cb);
    }

    private void upload(Uri uri, String bucket, String filename, String contentType, UploadCallback cb) {
        io.execute(() -> {
            try {
                ContentResolver resolver = FlzChatApp.get().getContentResolver();
                long size = 0;
                try (InputStream probe = resolver.openInputStream(uri)) {
                    if (probe != null) {
                        size = probe.available();
                    }
                }
                if (size <= 0) {
                    size = 1;
                }
                final long fileSize = size;
                api.presignUpload(new FileDtos.PresignUploadRequest(bucket, filename, contentType, fileSize))
                        .enqueue(new ApiCallback<FileDtos.PresignUploadResponse>() {
                            @Override
                            public void onSuccess(FileDtos.PresignUploadResponse data) {
                                if (data == null || data.uploadUrl == null || data.objectKey == null) {
                                    cb.onError("预签名失败");
                                    return;
                                }
                                io.execute(() -> putObject(uri, data.uploadUrl, contentType, data.objectKey, cb));
                            }

                            @Override
                            public void onError(String message) {
                                cb.onError(message);
                            }
                        });
            } catch (Exception e) {
                cb.onError(e.getMessage() != null ? e.getMessage() : "读取文件失败");
            }
        });
    }

    private void putObject(Uri uri, String uploadUrl, String contentType, String objectKey, UploadCallback cb) {
        try {
            ContentResolver resolver = FlzChatApp.get().getContentResolver();
            byte[] bytes;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) {
                    cb.onError("无法读取文件");
                    return;
                }
                bytes = readAll(in);
            }
            MediaType mediaType = MediaType.parse(contentType != null ? contentType : "application/octet-stream");
            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .put(RequestBody.create(bytes, mediaType))
                    .build();
            try (Response response = http.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    cb.onSuccess(objectKey);
                } else {
                    cb.onError("上传失败 HTTP " + response.code());
                }
            }
        } catch (Exception e) {
            cb.onError(e.getMessage() != null ? e.getMessage() : "上传失败");
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = in.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    private static String guessContentType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }
}
