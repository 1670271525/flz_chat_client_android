package com.flz.flz_chat.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 智能体会话：本地主键 + 服务端 session_id（flz_agent 多轮上下文键）。
 */
@Entity(
        tableName = "agent_sessions",
        indices = {@Index(value = {"ownerUserId", "updatedAt"})}
)
public class AgentSessionEntity {

    @PrimaryKey(autoGenerate = true)
    public long sessionId;
    public long ownerUserId;
    /** 展示标题 */
    public String title;
    /** 传给 flz_agent 的 session_id，如 chat_uuid */
    public String remoteSessionId;
    /** chat / code / draw / mcp */
    public String agentType;
    public long updatedAt;
}
