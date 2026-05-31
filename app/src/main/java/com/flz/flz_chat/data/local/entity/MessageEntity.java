package com.flz.flz_chat.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 消息本地缓存：HTTP 历史 + WS msg.new / msg.replay，按 messageId 去重。
 */
@Entity(
        tableName = "messages",
        indices = {
                @Index(value = {"ownerUserId", "conversationId"}),
                @Index(value = {"ownerUserId", "clientMsgId"})
        }
)
public class MessageEntity {

    @PrimaryKey(autoGenerate = true)
    public long localId;

    public long ownerUserId;
    public long messageId;
    public long conversationId;
    public long senderId;
    public int type;
    @NonNull
    public String content = "";
    public String clientMsgId;
    public String createdAt;
    /** pending=发送中, sent=已确认, failed=失败 */
    public String status;
    public boolean isSelf;
}
