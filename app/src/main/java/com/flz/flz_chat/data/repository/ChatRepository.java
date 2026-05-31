package com.flz.flz_chat.data.repository;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.data.local.AppDatabase;
import com.flz.flz_chat.data.local.entity.ConversationEntity;
import com.flz.flz_chat.data.local.entity.MessageEntity;
import com.flz.flz_chat.data.remote.ApiService;
import com.flz.flz_chat.data.remote.RetrofitClient;
import com.flz.flz_chat.data.remote.dto.ChatDtos;
import com.flz.flz_chat.util.ApiCallback;

import com.flz.flz_chat.util.TimeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatRepository {

    private final AppDatabase db = FlzChatApp.get().getDatabase();
    private final ApiService api = RetrofitClient.getApi();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private long ownerUserId() {
        return FlzChatApp.get().getSessionManager().getUserId();
    }

    public void syncConversations(Runnable onDone) {
        api.getConversations(1, 50).enqueue(new ApiCallback<com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.ConversationItem>>() {
            @Override
            public void onSuccess(com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.ConversationItem> data) {
                io.execute(() -> {
                    if (data != null && data.records != null) {
                        long ownerUserId = ownerUserId();
                        List<ConversationEntity> list = new ArrayList<>();
                        for (ChatDtos.ConversationItem item : data.records) {
                            ConversationEntity mapped = mapConversation(ownerUserId, item);
                            ConversationEntity existing = db.conversationDao()
                                    .getById(ownerUserId, mapped.conversationId);
                            int merged = mergeUnreadOnSync(
                                    ownerUserId, item, existing, mapped.unreadCount);
                            mapped.unreadCount = resolveUnreadCount(
                                    mapped.conversationId, item, ownerUserId, merged);
                            list.add(mapped);
                        }
                        db.conversationDao().upsertAll(list);
                    }
                    if (onDone != null) onDone.run();
                });
            }

            @Override
            public void onError(String message) {
                if (onDone != null) onDone.run();
            }
        });
    }

    public List<ConversationEntity> getLocalConversations() {
        return db.conversationDao().getAll(ownerUserId());
    }

    public void loadMessages(long conversationId, Runnable onDone) {
        api.getMessages(conversationId, null, 30).enqueue(new ApiCallback<com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.MessageItem>>() {
            @Override
            public void onSuccess(com.flz.flz_chat.data.remote.dto.PageResult<ChatDtos.MessageItem> data) {
                io.execute(() -> {
                    long myId = FlzChatApp.get().getSessionManager().getUserId();
                    if (data != null && data.records != null) {
                        for (ChatDtos.MessageItem m : data.records) {
                            saveIncomingFromWs(m.messageId, m.conversationId, m.senderId,
                                    m.type, m.content, null, m.createdAt, myId, false);
                        }
                    }
                    if (onDone != null) onDone.run();
                });
            }

            @Override
            public void onError(String message) {
                if (onDone != null) onDone.run();
            }
        });
    }

    public List<MessageEntity> getLocalMessages(long conversationId) {
        List<MessageEntity> list = db.messageDao().getByConversation(ownerUserId(), conversationId);
        Collections.sort(list, ChatRepository::compareMessages);
        return list;
    }

    /** 旧消息在上、新消息在下：优先按时间排序 */
    static int compareMessages(MessageEntity a, MessageEntity b) {
        long ta = TimeUtil.parseToMillis(a.createdAt);
        long tb = TimeUtil.parseToMillis(b.createdAt);
        if (ta > 0 && tb > 0 && ta != tb) {
            return Long.compare(ta, tb);
        }
        if (ta > 0 && tb <= 0) {
            return -1;
        }
        if (ta <= 0 && tb > 0) {
            return 1;
        }
        boolean aPending = a.messageId <= 0;
        boolean bPending = b.messageId <= 0;
        if (aPending && bPending) {
            return Long.compare(a.localId, b.localId);
        }
        if (aPending) {
            return 1;
        }
        if (bPending) {
            return -1;
        }
        int byId = Long.compare(a.messageId, b.messageId);
        if (byId != 0) {
            return byId;
        }
        return Long.compare(a.localId, b.localId);
    }

    public int getTotalUnreadCount() {
        return db.conversationDao().totalUnread(ownerUserId());
    }

    /** 同步写入待发送消息，便于 UI 立即刷新 */
    public void insertPendingSync(long conversationId, String content, String clientMsgId) {
        insertPendingSync(conversationId, 1, content, clientMsgId);
    }

    public void insertPendingSync(long conversationId, int type, String content, String clientMsgId) {
        MessageEntity e = new MessageEntity();
        e.ownerUserId = ownerUserId();
        e.messageId = 0;
        e.conversationId = conversationId;
        e.senderId = FlzChatApp.get().getSessionManager().getUserId();
        e.type = type;
        e.content = content != null ? content : "";
        e.clientMsgId = clientMsgId;
        e.createdAt = String.valueOf(System.currentTimeMillis());
        e.status = "pending";
        e.isSelf = true;
        db.messageDao().insert(e);
        String preview = type == 1 ? content : "[媒体消息]";
        db.conversationDao().updateLastMessage(ownerUserId(), conversationId, preview, 0, System.currentTimeMillis());
        refreshConversationUnread(conversationId);
    }

    public void sendImageMessage(long conversationId, String objectKey, String clientMsgId, ApiCallback<Long> cb) {
        ChatDtos.SendMessageRequest req = new ChatDtos.SendMessageRequest();
        req.conversationId = conversationId;
        req.type = 2;
        req.content = objectKey;
        req.clientMsgId = clientMsgId;
        req.mediaMeta = "{\"size\":0}";
        api.sendMessage(req).enqueue(new ApiCallback<com.flz.flz_chat.data.remote.dto.FileDtos.SendMessageResponse>() {
            @Override
            public void onSuccess(com.flz.flz_chat.data.remote.dto.FileDtos.SendMessageResponse data) {
                io.execute(() -> {
                    if (data != null && data.messageId > 0) {
                        db.messageDao().confirmSendWithContent(
                                ownerUserId(), clientMsgId, data.messageId, objectKey);
                    } else {
                        db.messageDao().markFailed(ownerUserId(), clientMsgId);
                    }
                });
                cb.onSuccess(data != null ? data.messageId : 0L);
            }

            @Override
            public void onError(String message) {
                io.execute(() -> db.messageDao().markFailed(ownerUserId(), clientMsgId));
                cb.onError(message);
            }
        });
    }

    public void confirmOutgoing(String clientMsgId, long messageId) {
        io.execute(() -> afterOutgoingConfirmed(clientMsgId, messageId));
    }

    /** 退出聊天页时上报已读，避免服务端把己方发送未确认的消息计入未读 */
    public void markConversationReadUpToLatest(long conversationId) {
        io.execute(() -> {
            Long maxId = db.messageDao().maxMessageId(ownerUserId(), conversationId);
            if (maxId != null && maxId > 0) {
                markConversationReadLocalAndRemote(conversationId, maxId);
            }
        });
    }

    private void afterOutgoingConfirmed(String clientMsgId, long messageId) {
        long owner = ownerUserId();
        db.messageDao().confirmSend(owner, clientMsgId, messageId);
        MessageEntity msg = db.messageDao().findByClientMsgId(owner, clientMsgId);
        if (msg == null || messageId <= 0) {
            return;
        }
        String preview = msg.type == 1 ? msg.content : "[媒体消息]";
        db.conversationDao().updateLastMessage(owner, msg.conversationId, preview, messageId,
                System.currentTimeMillis());
        markConversationReadLocalAndRemote(msg.conversationId, messageId);
    }

    private void markConversationReadLocalAndRemote(long conversationId, long lastReadMessageId) {
        FlzChatApp.get().getSessionManager().setConversationLastRead(conversationId, lastReadMessageId);
        refreshConversationUnread(conversationId);
        api.markRead(conversationId, new ChatDtos.ReadRequest(lastReadMessageId))
                .enqueue(new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
    }

    public void updatePendingContent(String clientMsgId, String content) {
        io.execute(() -> db.messageDao().updateContentByClientMsgId(ownerUserId(), clientMsgId, content));
    }

    /** 同步写入待发送消息（阻塞，便于发送流程立即更新 content） */
    public void insertPendingSyncBlocking(long conversationId, int type, String content, String clientMsgId) {
        MessageEntity e = new MessageEntity();
        e.ownerUserId = ownerUserId();
        e.messageId = 0;
        e.conversationId = conversationId;
        e.senderId = FlzChatApp.get().getSessionManager().getUserId();
        e.type = type;
        e.content = content != null ? content : "";
        e.clientMsgId = clientMsgId;
        e.createdAt = String.valueOf(System.currentTimeMillis());
        e.status = "pending";
        e.isSelf = true;
        db.messageDao().insert(e);
        String preview = type == 1 ? content : "[媒体消息]";
        db.conversationDao().updateLastMessage(ownerUserId(), conversationId, preview, 0, System.currentTimeMillis());
        refreshConversationUnread(conversationId);
    }

    public void updatePendingContentBlocking(String clientMsgId, String content) {
        db.messageDao().updateContentByClientMsgId(ownerUserId(), clientMsgId, content);
    }

    public void failOutgoing(String clientMsgId) {
        io.execute(() -> db.messageDao().markFailed(ownerUserId(), clientMsgId));
    }

    public MessageEntity saveIncomingFromWs(long messageId, long conversationId, long senderId,
                                            int type, String content, String clientMsgId,
                                            String createdAt, long myUserId) {
        return saveIncomingFromWs(messageId, conversationId, senderId, type, content,
                clientMsgId, createdAt, myUserId, true);
    }

    public MessageEntity saveIncomingFromWs(long messageId, long conversationId, long senderId,
                                            int type, String content, String clientMsgId,
                                            String createdAt, long myUserId, boolean countUnread) {
        long ownerUserId = ownerUserId();
        if (messageId > 0 && db.messageDao().countByMessageId(ownerUserId, conversationId, messageId) > 0) {
            return null;
        }
        MessageEntity pending = null;
        if (clientMsgId != null && !clientMsgId.isEmpty()) {
            pending = db.messageDao().findByClientMsgId(ownerUserId, clientMsgId);
        } else if (senderId == myUserId) {
            pending = db.messageDao().findOldestPendingSelf(ownerUserId, conversationId);
        }
        if (pending != null) {
            pending.messageId = messageId;
            pending.status = "sent";
            if (createdAt != null && !createdAt.isEmpty()) {
                pending.createdAt = createdAt;
            }
            if (type == 2 && content != null && !content.isEmpty()) {
                pending.content = content;
            }
            db.messageDao().insert(pending);
            String preview = type == 1 ? content : "[媒体消息]";
            db.conversationDao().updateLastMessage(ownerUserId, conversationId, preview, messageId, System.currentTimeMillis());
            if (messageId > 0) {
                markConversationReadLocalAndRemote(conversationId, messageId);
            } else {
                refreshConversationUnread(conversationId);
            }
            return pending;
        }
        MessageEntity e = new MessageEntity();
        e.ownerUserId = ownerUserId;
        e.messageId = messageId;
        e.conversationId = conversationId;
        e.senderId = senderId;
        e.type = type;
        e.content = content != null ? content : "";
        e.clientMsgId = clientMsgId;
        if (createdAt == null || createdAt.trim().isEmpty()) {
            e.createdAt = String.valueOf(messageId > 0 ? messageId : System.currentTimeMillis());
        } else {
            e.createdAt = createdAt;
        }
        e.status = "sent";
        e.isSelf = senderId == myUserId;
        db.messageDao().insert(e);
        String preview = type == 1 ? content : "[媒体消息]";
        db.conversationDao().updateLastMessage(ownerUserId, conversationId, preview, messageId, System.currentTimeMillis());
        if (e.isSelf) {
            if (messageId > 0) {
                markConversationReadLocalAndRemote(conversationId, messageId);
            } else {
                refreshConversationUnread(conversationId);
            }
        } else if (countUnread && senderId != myUserId) {
            refreshConversationUnread(conversationId);
        }
        return e;
    }

    /** 按本地消息与已读游标重算未读，避免服务端未读数污染列表与 Tab 角标 */
    private int computeUnreadForConversation(long conversationId) {
        long lastRead = FlzChatApp.get().getSessionManager().getConversationLastRead(conversationId);
        return db.messageDao().countPeerAfterRead(ownerUserId(), conversationId, lastRead);
    }

    private void refreshConversationUnread(long conversationId) {
        db.conversationDao().setUnread(ownerUserId(), conversationId,
                computeUnreadForConversation(conversationId));
    }

    private int resolveUnreadCount(long conversationId, ChatDtos.ConversationItem item,
                                   long ownerUserId, int mergedFromServer) {
        int computed = computeUnreadForConversation(conversationId);
        if (computed > 0) {
            return computed;
        }
        if (item.lastMessage != null && item.lastMessage.senderId != ownerUserId && mergedFromServer > 0) {
            return Math.min(mergedFromServer, 1);
        }
        return 0;
    }

    public void markConversationRead(long conversationId, long lastReadMessageId, Runnable onDone) {
        io.execute(() -> {
            markConversationReadLocalAndRemote(conversationId, lastReadMessageId);
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    /**
     * 合并 HTTP 同步的未读数：服务端可能把「己方已发但未上报已读」算进未读，
     * 此时应信任本地 WS 累加结果，避免一条对方消息显示成多条未读。
     */
    private int mergeUnreadOnSync(long ownerUserId, ChatDtos.ConversationItem item,
                                  ConversationEntity existing, int mappedUnread) {
        if (item.lastMessage != null && item.lastMessage.senderId == ownerUserId) {
            return 0;
        }
        if (existing == null) {
            return mappedUnread;
        }
        if (existing.unreadCount == 0 && mappedUnread > 0
                && item.lastMessageId != null && item.lastMessageId <= existing.lastMessageId) {
            return 0;
        }
        int local = existing.unreadCount;
        int server = mappedUnread;
        if (item.lastMessage != null && item.lastMessage.senderId != ownerUserId
                && server > local + 1) {
            return local > 0 ? local : 1;
        }
        return Math.max(server, local);
    }

    public void createSingleChat(long peerUserId, ApiCallback<Long> cb) {
        api.createSingle(new ChatDtos.SingleChatRequest(peerUserId))
                .enqueue(new ApiCallback<ChatDtos.ConversationIdResponse>() {
                    @Override
                    public void onSuccess(ChatDtos.ConversationIdResponse data) {
                        if (data != null) {
                            cb.onSuccess(data.conversationId);
                        } else {
                            cb.onError("无会话 ID");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    public void clearLocalCacheForOwner(long ownerUserId) {
        db.messageDao().deleteAllForOwner(ownerUserId);
        db.conversationDao().deleteAllForOwner(ownerUserId);
    }

    private ConversationEntity mapConversation(long ownerUserId, ChatDtos.ConversationItem item) {
        ConversationEntity e = new ConversationEntity();
        e.ownerUserId = ownerUserId;
        e.conversationId = item.conversationId;
        e.type = item.type;
        e.title = item.name;
        e.avatarUrl = item.avatarUrl;
        if (item.lastMessage != null && item.lastMessage.senderId == ownerUserId) {
            e.unreadCount = 0;
        } else {
            e.unreadCount = item.unreadCount;
        }
        e.pinned = item.pinned;
        e.lastMessageId = item.lastMessageId != null ? item.lastMessageId : 0;
        if (item.lastMessage != null) {
            e.lastPreview = item.lastMessage.preview;
        }
        if (item.peer != null) {
            e.peerUserId = item.peer.userId;
            e.peerNickname = item.peer.nickname;
            if (e.title == null || e.title.isEmpty()) {
                e.title = item.peer.nickname;
            }
            if (item.peer.avatarUrl != null && !item.peer.avatarUrl.isEmpty()) {
                e.avatarUrl = item.peer.avatarUrl;
            }
        }
        if ((e.avatarUrl == null || e.avatarUrl.isEmpty()) && item.avatarUrl != null) {
            e.avatarUrl = item.avatarUrl;
        }
        e.updatedAt = System.currentTimeMillis();
        return e;
    }
}
