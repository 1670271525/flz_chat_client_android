package com.flz.flz_chat.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class ChatDtos {

    private ChatDtos() {}

    public static class ConversationItem {
        public long conversationId;
        public int type;
        public String name;
        public String avatarUrl;
        public Long lastMessageId;
        public LastMessage lastMessage;
        public int unreadCount;
        public boolean pinned;
        public boolean mute;
        public Peer peer;
    }

    public static class LastMessage {
        public int type;
        public String preview;
        public long senderId;
        public String createdAt;
    }

    public static class Peer {
        public long userId;
        public String nickname;
        public String avatarUrl;
    }

    public static class MessageItem {
        public long messageId;
        public long conversationId;
        public long senderId;
        public int type;
        public String content;
        public String createdAt;
        public String downloadUrl;
    }

    public static class SendMessageRequest {
        public long conversationId;
        public int type;
        public String content;
        public String clientMsgId;
        public String mediaMeta;
    }

    public static class ReadRequest {
        public long lastReadMessageId;

        public ReadRequest(long lastReadMessageId) {
            this.lastReadMessageId = lastReadMessageId;
        }
    }

    public static class SingleChatRequest {
        public long peerUserId;

        public SingleChatRequest(long peerUserId) {
            this.peerUserId = peerUserId;
        }
    }

    public static class ConversationIdResponse {
        public long conversationId;
    }

    public static class FriendItem {
        public long userId;
        public String alias;
        public String nickname;
        public String avatarUrl;
        public String signature;
    }

    public static class FriendRequestItem {
        @SerializedName(value = "requestId", alternate = {"id", "request_id"})
        public long requestId;
        public long fromUserId;
        public String fromNickname;
        public long toUserId;
        public String remark;
        public int status;

        public long resolvedRequestId() {
            return requestId;
        }
    }

    public static class FriendAliasBody {
        public String alias;

        public FriendAliasBody(String alias) {
            this.alias = alias;
        }
    }

    public static class FriendRequestBody {
        public long toUserId;
        public String remark;

        public FriendRequestBody(long toUserId, String remark) {
            this.toUserId = toUserId;
            this.remark = remark;
        }
    }

    public static class SocialPostItem {
        public long socialId;
        @SerializedName(value = "userId", alternate = {"authorId", "author_id"})
        public long userId;
        public String userName;
        public String nickname;
        public String content;
        public int visibility;
        public String createdAt;
        @SerializedName(value = "avatarUrl", alternate = {"avatar"})
        public String avatarUrl;
        public int likeCount;
        @com.google.gson.annotations.SerializedName(value = "liked", alternate = {"isLiked", "hasLiked"})
        public boolean liked;
        public List<SocialImage> images;
        public Author author;
    }

    public static class Author {
        @SerializedName(value = "userId", alternate = {"id"})
        public long userId;
        public String nickname;
        public String userName;
        @SerializedName(value = "avatarUrl", alternate = {"avatar"})
        public String avatarUrl;
    }

    public static class SocialImage {
        public String imageUrl;
        public int sortOrder;
    }

    public static class PostSocialRequest {
        public String content;
        public int visibility;

        public PostSocialRequest(String content, int visibility) {
            this.content = content;
            this.visibility = visibility;
        }
    }
}
