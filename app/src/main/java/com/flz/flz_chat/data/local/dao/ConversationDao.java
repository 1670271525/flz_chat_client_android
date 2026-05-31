package com.flz.flz_chat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.flz.flz_chat.data.local.entity.ConversationEntity;

import java.util.List;

@Dao
public interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE ownerUserId=:ownerUserId ORDER BY pinned DESC, updatedAt DESC")
    List<ConversationEntity> getAll(long ownerUserId);

    @Query("SELECT * FROM conversations WHERE ownerUserId=:ownerUserId AND conversationId=:id LIMIT 1")
    ConversationEntity getById(long ownerUserId, long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ConversationEntity> list);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ConversationEntity entity);

    @Query("UPDATE conversations SET lastPreview=:preview, lastMessageId=:msgId, updatedAt=:ts " +
            "WHERE ownerUserId=:ownerUserId AND conversationId=:id")
    void updateLastMessage(long ownerUserId, long id, String preview, long msgId, long ts);

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 " +
            "WHERE ownerUserId=:ownerUserId AND conversationId=:id")
    void incrementUnread(long ownerUserId, long id);

    @Query("UPDATE conversations SET unreadCount=0 WHERE ownerUserId=:ownerUserId AND conversationId=:id")
    void clearUnread(long ownerUserId, long id);

    @Query("UPDATE conversations SET unreadCount=:count WHERE ownerUserId=:ownerUserId AND conversationId=:id")
    void setUnread(long ownerUserId, long id, int count);

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations WHERE ownerUserId=:ownerUserId")
    int totalUnread(long ownerUserId);

    @Query("DELETE FROM conversations WHERE ownerUserId=:ownerUserId")
    void deleteAllForOwner(long ownerUserId);
}
