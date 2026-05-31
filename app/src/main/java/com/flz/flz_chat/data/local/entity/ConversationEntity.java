package com.flz.flz_chat.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;

/**
 * 会话列表本地缓存，来源 GET /api/conversations 及 WS conversation.created。
 */
@Entity(
        tableName = "conversations",
        primaryKeys = {"ownerUserId", "conversationId"},
        indices = {@Index(value = {"ownerUserId", "conversationId"}, unique = true)}
)
public class ConversationEntity {

    public long ownerUserId;
    public long conversationId;
    public int type;
    public String title;
    public String avatarUrl;
    public String lastPreview;
    public long lastMessageId;
    public int unreadCount;
    public boolean pinned;
    public long peerUserId;
    public String peerNickname;
    public long updatedAt;
}
