package com.flz.flz_chat.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.flz.flz_chat.data.local.dao.AgentDao;
import com.flz.flz_chat.data.local.dao.ConversationDao;
import com.flz.flz_chat.data.local.dao.FriendDao;
import com.flz.flz_chat.data.local.dao.MessageDao;
import com.flz.flz_chat.data.local.dao.SocialDao;
import com.flz.flz_chat.data.local.entity.AgentMessageEntity;
import com.flz.flz_chat.data.local.entity.AgentSessionEntity;
import com.flz.flz_chat.data.local.entity.ConversationEntity;
import com.flz.flz_chat.data.local.entity.FriendEntity;
import com.flz.flz_chat.data.local.entity.MessageEntity;
import com.flz.flz_chat.data.local.entity.SocialEntity;

@Database(
        entities = {
                ConversationEntity.class,
                MessageEntity.class,
                FriendEntity.class,
                SocialEntity.class,
                AgentSessionEntity.class,
                AgentMessageEntity.class
        },
        version = 5,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            migrateToOwnerScopedV3(db);
        }
    };

    /** 修复 v3 迁移遗留：messages 列 DEFAULT、旧索引，以及 agent 表 ALTER 默认值问题 */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            normalizeOwnerScopedSchema(db);
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            if (!columnExists(db, "social_posts", "avatarUrl")) {
                db.execSQL("ALTER TABLE `social_posts` ADD COLUMN `avatarUrl` TEXT");
            }
        }
    };

    public abstract ConversationDao conversationDao();
    public abstract MessageDao messageDao();
    public abstract FriendDao friendDao();
    public abstract SocialDao socialDao();
    public abstract AgentDao agentDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "flz_chat.db")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static void migrateToOwnerScopedV3(@NonNull SupportSQLiteDatabase db) {
        rebuildConversations(db);
        rebuildMessages(db);
        rebuildFriends(db);
        rebuildSocialPosts(db);
        rebuildAgentSessions(db);
        rebuildAgentMessages(db);
    }

    private static void normalizeOwnerScopedSchema(@NonNull SupportSQLiteDatabase db) {
        rebuildConversations(db);
        rebuildMessages(db);
        rebuildFriends(db);
        rebuildSocialPosts(db);
        rebuildAgentSessions(db);
        rebuildAgentMessages(db);
    }

    private static void rebuildConversations(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `conversations_new` (" +
                "`ownerUserId` INTEGER NOT NULL, " +
                "`conversationId` INTEGER NOT NULL, " +
                "`type` INTEGER NOT NULL, " +
                "`title` TEXT, " +
                "`avatarUrl` TEXT, " +
                "`lastPreview` TEXT, " +
                "`lastMessageId` INTEGER NOT NULL, " +
                "`unreadCount` INTEGER NOT NULL, " +
                "`pinned` INTEGER NOT NULL, " +
                "`peerUserId` INTEGER NOT NULL, " +
                "`peerNickname` TEXT, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`ownerUserId`, `conversationId`))");
        if (tableExists(db, "conversations")) {
            if (columnExists(db, "conversations", "ownerUserId")) {
                db.execSQL("INSERT INTO `conversations_new` " +
                        "(`ownerUserId`,`conversationId`,`type`,`title`,`avatarUrl`,`lastPreview`,`lastMessageId`,`unreadCount`,`pinned`,`peerUserId`,`peerNickname`,`updatedAt`) " +
                        "SELECT `ownerUserId`,`conversationId`,`type`,`title`,`avatarUrl`,`lastPreview`,`lastMessageId`,`unreadCount`,`pinned`,`peerUserId`,`peerNickname`,`updatedAt` FROM `conversations`");
            } else {
                db.execSQL("INSERT INTO `conversations_new` " +
                        "(`ownerUserId`,`conversationId`,`type`,`title`,`avatarUrl`,`lastPreview`,`lastMessageId`,`unreadCount`,`pinned`,`peerUserId`,`peerNickname`,`updatedAt`) " +
                        "SELECT 0,`conversationId`,`type`,`title`,`avatarUrl`,`lastPreview`,`lastMessageId`,`unreadCount`,`pinned`,`peerUserId`,`peerNickname`,`updatedAt` FROM `conversations`");
            }
            db.execSQL("DROP TABLE `conversations`");
        }
        db.execSQL("ALTER TABLE `conversations_new` RENAME TO `conversations`");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_ownerUserId_conversationId` " +
                "ON `conversations` (`ownerUserId`, `conversationId`)");
    }

    private static void rebuildMessages(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS `index_messages_conversationId`");
        db.execSQL("DROP INDEX IF EXISTS `index_messages_clientMsgId`");
        db.execSQL("DROP INDEX IF EXISTS `index_messages_ownerUserId_conversationId`");
        db.execSQL("DROP INDEX IF EXISTS `index_messages_ownerUserId_clientMsgId`");

        db.execSQL("CREATE TABLE IF NOT EXISTS `messages_new` (" +
                "`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` INTEGER NOT NULL, " +
                "`messageId` INTEGER NOT NULL, " +
                "`conversationId` INTEGER NOT NULL, " +
                "`senderId` INTEGER NOT NULL, " +
                "`type` INTEGER NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`clientMsgId` TEXT, " +
                "`createdAt` TEXT, " +
                "`status` TEXT, " +
                "`isSelf` INTEGER NOT NULL)");
        if (tableExists(db, "messages")) {
            if (columnExists(db, "messages", "ownerUserId")) {
                db.execSQL("INSERT INTO `messages_new` " +
                        "(`localId`,`ownerUserId`,`messageId`,`conversationId`,`senderId`,`type`,`content`,`clientMsgId`,`createdAt`,`status`,`isSelf`) " +
                        "SELECT `localId`,`ownerUserId`,`messageId`,`conversationId`,`senderId`,`type`,`content`,`clientMsgId`,`createdAt`,`status`,`isSelf` FROM `messages`");
            } else {
                db.execSQL("INSERT INTO `messages_new` " +
                        "(`localId`,`ownerUserId`,`messageId`,`conversationId`,`senderId`,`type`,`content`,`clientMsgId`,`createdAt`,`status`,`isSelf`) " +
                        "SELECT `localId`,0,`messageId`,`conversationId`,`senderId`,`type`,`content`,`clientMsgId`,`createdAt`,`status`,`isSelf` FROM `messages`");
            }
            db.execSQL("DROP TABLE `messages`");
        }
        db.execSQL("ALTER TABLE `messages_new` RENAME TO `messages`");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_ownerUserId_conversationId` " +
                "ON `messages` (`ownerUserId`, `conversationId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_ownerUserId_clientMsgId` " +
                "ON `messages` (`ownerUserId`, `clientMsgId`)");
    }

    private static void rebuildFriends(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `friends_new` (" +
                "`ownerUserId` INTEGER NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`alias` TEXT, " +
                "`nickname` TEXT, " +
                "`avatarUrl` TEXT, " +
                "`signature` TEXT, " +
                "PRIMARY KEY(`ownerUserId`, `userId`))");
        if (tableExists(db, "friends")) {
            if (columnExists(db, "friends", "ownerUserId")) {
                db.execSQL("INSERT INTO `friends_new` (`ownerUserId`,`userId`,`alias`,`nickname`,`avatarUrl`,`signature`) " +
                        "SELECT `ownerUserId`,`userId`,`alias`,`nickname`,`avatarUrl`,`signature` FROM `friends`");
            } else {
                db.execSQL("INSERT INTO `friends_new` (`ownerUserId`,`userId`,`alias`,`nickname`,`avatarUrl`,`signature`) " +
                        "SELECT 0,`userId`,`alias`,`nickname`,`avatarUrl`,`signature` FROM `friends`");
            }
            db.execSQL("DROP TABLE `friends`");
        }
        db.execSQL("ALTER TABLE `friends_new` RENAME TO `friends`");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_friends_ownerUserId_userId` " +
                "ON `friends` (`ownerUserId`, `userId`)");
    }

    private static void rebuildSocialPosts(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `social_posts_new` (" +
                "`ownerUserId` INTEGER NOT NULL, " +
                "`socialId` INTEGER NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`nickname` TEXT, " +
                "`avatarUrl` TEXT, " +
                "`content` TEXT, " +
                "`createdAt` TEXT, " +
                "`likeCount` INTEGER NOT NULL, " +
                "`liked` INTEGER NOT NULL, " +
                "PRIMARY KEY(`ownerUserId`, `socialId`))");
        if (tableExists(db, "social_posts")) {
            boolean hasAvatar = columnExists(db, "social_posts", "avatarUrl");
            if (columnExists(db, "social_posts", "ownerUserId")) {
                if (hasAvatar) {
                    db.execSQL("INSERT INTO `social_posts_new` (`ownerUserId`,`socialId`,`userId`,`nickname`,`avatarUrl`,`content`,`createdAt`,`likeCount`,`liked`) " +
                            "SELECT `ownerUserId`,`socialId`,`userId`,`nickname`,`avatarUrl`,`content`,`createdAt`,`likeCount`,`liked` FROM `social_posts`");
                } else {
                    db.execSQL("INSERT INTO `social_posts_new` (`ownerUserId`,`socialId`,`userId`,`nickname`,`avatarUrl`,`content`,`createdAt`,`likeCount`,`liked`) " +
                            "SELECT `ownerUserId`,`socialId`,`userId`,`nickname`,NULL,`content`,`createdAt`,`likeCount`,`liked` FROM `social_posts`");
                }
            } else {
                db.execSQL("INSERT INTO `social_posts_new` (`ownerUserId`,`socialId`,`userId`,`nickname`,`avatarUrl`,`content`,`createdAt`,`likeCount`,`liked`) " +
                        "SELECT 0,`socialId`,`userId`,`nickname`,NULL,`content`,`createdAt`,`likeCount`,`liked` FROM `social_posts`");
            }
            db.execSQL("DROP TABLE `social_posts`");
        }
        db.execSQL("ALTER TABLE `social_posts_new` RENAME TO `social_posts`");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_social_posts_ownerUserId_socialId` " +
                "ON `social_posts` (`ownerUserId`, `socialId`)");
    }

    private static void rebuildAgentSessions(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS `index_agent_sessions_ownerUserId_updatedAt`");
        db.execSQL("CREATE TABLE IF NOT EXISTS `agent_sessions_new` (" +
                "`sessionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` INTEGER NOT NULL, " +
                "`title` TEXT, " +
                "`remoteSessionId` TEXT, " +
                "`agentType` TEXT, " +
                "`updatedAt` INTEGER NOT NULL)");
        if (tableExists(db, "agent_sessions")) {
            if (columnExists(db, "agent_sessions", "ownerUserId")) {
                db.execSQL("INSERT INTO `agent_sessions_new` (`sessionId`,`ownerUserId`,`title`,`remoteSessionId`,`agentType`,`updatedAt`) " +
                        "SELECT `sessionId`,`ownerUserId`,`title`,`remoteSessionId`,`agentType`,`updatedAt` FROM `agent_sessions`");
            } else {
                db.execSQL("INSERT INTO `agent_sessions_new` (`sessionId`,`ownerUserId`,`title`,`remoteSessionId`,`agentType`,`updatedAt`) " +
                        "SELECT `sessionId`,0,`title`,`remoteSessionId`,`agentType`,`updatedAt` FROM `agent_sessions`");
            }
            db.execSQL("DROP TABLE `agent_sessions`");
        }
        db.execSQL("ALTER TABLE `agent_sessions_new` RENAME TO `agent_sessions`");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_sessions_ownerUserId_updatedAt` " +
                "ON `agent_sessions` (`ownerUserId`, `updatedAt`)");
    }

    private static void rebuildAgentMessages(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS `index_agent_messages_ownerUserId_sessionId_createdAt`");
        db.execSQL("CREATE TABLE IF NOT EXISTS `agent_messages_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` INTEGER NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`role` TEXT, " +
                "`content` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`status` TEXT)");
        if (tableExists(db, "agent_messages")) {
            if (columnExists(db, "agent_messages", "ownerUserId")) {
                db.execSQL("INSERT INTO `agent_messages_new` (`id`,`ownerUserId`,`sessionId`,`role`,`content`,`createdAt`,`status`) " +
                        "SELECT `id`,`ownerUserId`,`sessionId`,`role`,`content`,`createdAt`,`status` FROM `agent_messages`");
            } else {
                db.execSQL("INSERT INTO `agent_messages_new` (`id`,`ownerUserId`,`sessionId`,`role`,`content`,`createdAt`,`status`) " +
                        "SELECT `id`,0,`sessionId`,`role`,`content`,`createdAt`,`status` FROM `agent_messages`");
            }
            db.execSQL("DROP TABLE `agent_messages`");
        }
        db.execSQL("ALTER TABLE `agent_messages_new` RENAME TO `agent_messages`");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_messages_ownerUserId_sessionId_createdAt` " +
                "ON `agent_messages` (`ownerUserId`, `sessionId`, `createdAt`)");
    }

    private static boolean tableExists(@NonNull SupportSQLiteDatabase db, @NonNull String table) {
        android.database.Cursor cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{table});
        try {
            return cursor.getCount() > 0;
        } finally {
            cursor.close();
        }
    }

    private static boolean columnExists(@NonNull SupportSQLiteDatabase db,
                                        @NonNull String table,
                                        @NonNull String column) {
        android.database.Cursor cursor = db.query("PRAGMA table_info(`" + table + "`)");
        try {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }
}
