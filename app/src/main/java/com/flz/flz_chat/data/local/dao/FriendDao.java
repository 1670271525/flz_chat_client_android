package com.flz.flz_chat.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.flz.flz_chat.data.local.entity.FriendEntity;

import java.util.List;

@Dao
public interface FriendDao {

    @Query("SELECT * FROM friends WHERE ownerUserId=:ownerUserId ORDER BY nickname ASC")
    List<FriendEntity> getAll(long ownerUserId);

    @Query("SELECT avatarUrl FROM friends WHERE ownerUserId=:ownerUserId AND userId=:userId LIMIT 1")
    String getAvatarUrl(long ownerUserId, long userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<FriendEntity> list);

    @Query("DELETE FROM friends WHERE ownerUserId=:ownerUserId")
    void deleteAllForOwner(long ownerUserId);
}
