-- =====================================================================
-- flz_chat 业务库 一键建库脚本
-- 用法: mysql -uroot -p < 00_init.sql
-- 顺序: 库 → user → user_information → friendships → friend_requests
--       → conversations → conversation_participants → message
--       → user_social → user_social_image → user_social_like
-- =====================================================================
CREATE DATABASE IF NOT EXISTS `flz_chat`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE       utf8mb4_unicode_ci;

USE `flz_chat`;

SOURCE 01_user.sql;
SOURCE 02_user_information.sql;
SOURCE 03_friendships.sql;
SOURCE 04_friend_requests.sql;
SOURCE 05_conversations.sql;
SOURCE 06_conversation_participants.sql;
SOURCE 07_message.sql;
SOURCE 08_user_social.sql;
SOURCE 09_user_social_image.sql;
SOURCE 10_user_social_like.sql;
SOURCE 11_message_user_delete.sql;
