package com.flz.flz_chat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.flz.flz_chat.data.local.entity.AgentMessageEntity;
import com.flz.flz_chat.data.local.entity.AgentSessionEntity;

import java.util.List;

@Dao
public interface AgentDao {

    @Query("SELECT * FROM agent_sessions s " +
            "WHERE s.ownerUserId=:ownerUserId " +
            "AND EXISTS (SELECT 1 FROM agent_messages m " +
            "            WHERE m.ownerUserId=:ownerUserId " +
            "            AND m.sessionId=s.sessionId " +
            "            AND m.role='user') " +
            "ORDER BY s.updatedAt DESC")
    List<AgentSessionEntity> getSessions(long ownerUserId);

    @Query("SELECT * FROM agent_sessions WHERE ownerUserId=:ownerUserId AND sessionId=:id LIMIT 1")
    AgentSessionEntity getSession(long ownerUserId, long id);

    @Insert
    long insertSession(AgentSessionEntity session);

    @Query("UPDATE agent_sessions SET title=:title, updatedAt=:ts, agentType=:type " +
            "WHERE ownerUserId=:ownerUserId AND sessionId=:id")
    void updateSession(long ownerUserId, long id, String title, long ts, String type);

    @Query("UPDATE agent_sessions SET remoteSessionId=:rid WHERE ownerUserId=:ownerUserId AND sessionId=:id")
    void setRemoteSessionId(long ownerUserId, long id, String rid);

    @Query("SELECT * FROM agent_messages WHERE ownerUserId=:ownerUserId AND sessionId=:sid ORDER BY createdAt ASC")
    List<AgentMessageEntity> getMessages(long ownerUserId, long sid);

    @Insert
    long insertMessage(AgentMessageEntity msg);

    @Query("UPDATE agent_messages SET content=:content, status=:status WHERE ownerUserId=:ownerUserId AND id=:id")
    void updateMessage(long ownerUserId, long id, String content, String status);

    @Query("DELETE FROM agent_messages WHERE ownerUserId=:ownerUserId")
    void deleteMessagesForOwner(long ownerUserId);

    @Query("DELETE FROM agent_sessions WHERE ownerUserId=:ownerUserId")
    void deleteSessionsForOwner(long ownerUserId);
}
