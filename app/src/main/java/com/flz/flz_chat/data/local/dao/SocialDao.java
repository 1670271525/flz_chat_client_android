package com.flz.flz_chat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.flz.flz_chat.data.local.entity.SocialEntity;

import java.util.List;

@Dao
public interface SocialDao {

    @Query("SELECT * FROM social_posts WHERE ownerUserId=:ownerUserId ORDER BY createdAt DESC")
    List<SocialEntity> getAll(long ownerUserId);

    @Query("SELECT * FROM social_posts WHERE ownerUserId=:ownerUserId AND socialId=:socialId LIMIT 1")
    SocialEntity findBySocialId(long ownerUserId, long socialId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<SocialEntity> list);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SocialEntity entity);

    @Query("UPDATE social_posts SET liked=:liked, likeCount=:count " +
            "WHERE ownerUserId=:ownerUserId AND socialId=:id")
    void updateLike(long ownerUserId, long id, boolean liked, int count);

    @Query("DELETE FROM social_posts WHERE ownerUserId=:ownerUserId AND (socialId<=0 OR userId<=0)")
    void deleteInvalid(long ownerUserId);

    @Query("DELETE FROM social_posts WHERE ownerUserId=:ownerUserId")
    void deleteAllForOwner(long ownerUserId);
}
