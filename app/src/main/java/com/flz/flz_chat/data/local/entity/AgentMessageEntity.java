package com.flz.flz_chat.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "agent_messages",
        indices = {@Index(value = {"ownerUserId", "sessionId", "createdAt"})}
)
public class AgentMessageEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;
    public long ownerUserId;
    public long sessionId;
    /** user | assistant | system */
    public String role;
    public String content;
    public long createdAt;
    /** sent | streaming | error */
    public String status;
}
