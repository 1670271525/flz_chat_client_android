package com.flz.flz_chat.data.remote.dto;

public final class FileDtos {

    private FileDtos() {}

    public static class PresignUploadRequest {
        public String bucket;
        public String filename;
        public String contentType;
        public long size;

        public PresignUploadRequest(String bucket, String filename, String contentType, long size) {
            this.bucket = bucket;
            this.filename = filename;
            this.contentType = contentType;
            this.size = size;
        }
    }

    public static class PresignUploadResponse {
        public String objectKey;
        public String uploadUrl;
        public int expireSeconds;
    }

    public static class PresignDownloadResponse {
        public String url;
        public int expireSeconds;
    }

    public static class SendMessageResponse {
        public long messageId;
        public long conversationId;
        public String createdAt;
        public String downloadUrl;
    }
}
