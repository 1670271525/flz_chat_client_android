package com.flz.flz_chat.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;

@Entity(
        tableName = "friends",
        primaryKeys = {"ownerUserId", "userId"},
        indices = {@Index(value = {"ownerUserId", "userId"}, unique = true)}
)
public class FriendEntity {

    public long ownerUserId;
    public long userId;
    public String alias;
    public String nickname;
    public String avatarUrl;
    public String signature;
}
