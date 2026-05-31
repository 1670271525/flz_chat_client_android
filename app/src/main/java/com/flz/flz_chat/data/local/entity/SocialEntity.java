package com.flz.flz_chat.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;

@Entity(
        tableName = "social_posts",
        primaryKeys = {"ownerUserId", "socialId"},
        indices = {@Index(value = {"ownerUserId", "socialId"}, unique = true)}
)
public class SocialEntity {

    public long ownerUserId;
    public long socialId;
    public long userId;
    public String nickname;
    public String avatarUrl;
    public String content;
    public String createdAt;
    public int likeCount;
    public boolean liked;
}
