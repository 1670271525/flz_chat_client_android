package com.flz.flz_chat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.flz.flz_chat.data.local.entity.MessageEntity;

import java.util.List;

@Dao
public interface MessageDao {

    @Query("SELECT * FROM messages WHERE ownerUserId=:ownerUserId AND conversationId=:cid " +
            "ORDER BY (messageId = 0), messageId ASC, localId ASC")
    List<MessageEntity> getByConversation(long ownerUserId, long cid);

    @Query("SELECT COUNT(*) FROM messages WHERE ownerUserId=:ownerUserId AND conversationId=:cid AND messageId=:mid")
    int countByMessageId(long ownerUserId, long cid, long mid);

    @Query("SELECT * FROM messages WHERE ownerUserId=:ownerUserId AND clientMsgId=:clientMsgId LIMIT 1")
    MessageEntity findByClientMsgId(long ownerUserId, String clientMsgId);

    @Query("SELECT * FROM messages WHERE ownerUserId=:ownerUserId AND conversationId=:cid " +
            "AND status='pending' AND isSelf=1 ORDER BY localId ASC LIMIT 1")
    MessageEntity findOldestPendingSelf(long ownerUserId, long cid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MessageEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MessageEntity> list);

    @Query("UPDATE messages SET messageId=:mid, status='sent' " +
            "WHERE ownerUserId=:ownerUserId AND clientMsgId=:clientMsgId")
    void confirmSend(long ownerUserId, String clientMsgId, long mid);

    @Query("UPDATE messages SET messageId=:mid, content=:content, status='sent' " +
            "WHERE ownerUserId=:ownerUserId AND clientMsgId=:clientMsgId")
    void confirmSendWithContent(long ownerUserId, String clientMsgId, long mid, String content);

    @Query("UPDATE messages SET content=:content WHERE ownerUserId=:ownerUserId AND clientMsgId=:clientMsgId")
    void updateContentByClientMsgId(long ownerUserId, String clientMsgId, String content);

    @Query("UPDATE messages SET status='failed' WHERE ownerUserId=:ownerUserId AND clientMsgId=:clientMsgId")
    void markFailed(long ownerUserId, String clientMsgId);

    @Query("SELECT MAX(messageId) FROM messages WHERE ownerUserId=:ownerUserId AND conversationId=:cid")
    Long maxMessageId(long ownerUserId, long cid);

    @Query("SELECT COUNT(*) FROM messages WHERE ownerUserId=:ownerUserId AND conversationId=:cid " +
            "AND isSelf=0 AND messageId > :lastReadMessageId AND messageId > 0")
    int countPeerAfterRead(long ownerUserId, long cid, long lastReadMessageId);

    @Query("DELETE FROM messages WHERE ownerUserId=:ownerUserId")
    void deleteAllForOwner(long ownerUserId);
}
