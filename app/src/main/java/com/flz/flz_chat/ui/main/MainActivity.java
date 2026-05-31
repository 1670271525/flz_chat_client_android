package com.flz.flz_chat.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.R;
import com.flz.flz_chat.data.remote.WsChatManager;
import com.flz.flz_chat.data.repository.ChatRepository;
import com.flz.flz_chat.data.repository.FriendRepository;
import com.flz.flz_chat.data.repository.SocialRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.ui.RealtimeRefreshable;
import com.flz.flz_chat.ui.agent.AgentListFragment;
import com.flz.flz_chat.ui.auth.LoginActivity;
import com.flz.flz_chat.ui.chat.ConversationListFragment;
import com.flz.flz_chat.ui.profile.FriendFragment;
import com.flz.flz_chat.ui.profile.ProfileFragment;
import com.flz.flz_chat.ui.social.SocialFeedFragment;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.Executors;

/**
 * 主界面：底部 Tab（聊天 / 好友 / 智能体 / 社交 / 我）。
 */
public class MainActivity extends AppCompatActivity implements WsChatManager.Listener {

    private View wsBanner;
    private TextView tvWsStatus;
    private BottomNavigationView bottomNav;
    private final ChatRepository chatRepo = new ChatRepository();
    private final FriendRepository friendRepo = new FriendRepository();
    private final SocialRepository socialRepo = new SocialRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireLogin(this)) {
            return;
        }
        setContentView(R.layout.activity_main);

        wsBanner = findViewById(R.id.wsBanner);
        tvWsStatus = findViewById(R.id.tvWsStatus);
        bottomNav = findViewById(R.id.bottomNav);

        if (savedInstanceState == null) {
            showFragment(new ConversationListFragment());
            bottomNav.setSelectedItemId(R.id.nav_chat);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f;
            if (id == R.id.nav_chat) {
                f = new ConversationListFragment();
            } else if (id == R.id.nav_friends) {
                f = new FriendFragment();
            } else if (id == R.id.nav_agent) {
                f = new AgentListFragment();
            } else if (id == R.id.nav_social) {
                f = new SocialFeedFragment();
            } else {
                f = new ProfileFragment();
            }
            showFragment(f);
            if (id == R.id.nav_social) {
                refreshNavBadges();
            }
            return true;
        });

        WsChatManager ws = FlzChatApp.get().getWsChatManager();
        ws.addListener(this);
        ws.connectIfLoggedIn();
        updateConnectionBanner(ws);

        chatRepo.syncConversations(() -> runOnUiThread(this::refreshNavBadges));
        friendRepo.syncFriends(null);
        socialRepo.syncFeed(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNavBadges();
    }

    @Override
    protected void onDestroy() {
        FlzChatApp.get().getWsChatManager().removeListener(this);
        super.onDestroy();
    }

    private void showFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    public void refreshNavBadges() {
        if (bottomNav == null) {
            return;
        }
        int selectedId = bottomNav.getSelectedItemId();
        if (selectedId == R.id.nav_social) {
            socialRepo.markFeedSeen(() -> runOnUiThread(() -> applyBadge(R.id.nav_social, 0)));
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            int chatUnread = chatRepo.getTotalUnreadCount();
            int socialNew = selectedId == R.id.nav_social ? 0 : socialRepo.countUnseenPosts();
            runOnUiThread(() -> {
                applyBadge(R.id.nav_chat, chatUnread);
                if (selectedId != R.id.nav_social) {
                    applyBadge(R.id.nav_social, socialNew);
                }
            });
        });
        friendRepo.countPendingIncoming(new ApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer data) {
                runOnUiThread(() -> applyBadge(R.id.nav_friends, data != null ? data : 0));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> applyBadge(R.id.nav_friends, 0));
            }
        });
    }

    private void applyBadge(int menuItemId, int count) {
        if (bottomNav == null) {
            return;
        }
        if (count > 0) {
            BadgeDrawable badge = bottomNav.getOrCreateBadge(menuItemId);
            badge.setVisible(true);
            badge.setNumber(count);
            badge.setBackgroundColor(getColor(R.color.unread_dot));
            badge.setBadgeTextColor(getColor(R.color.white));
        } else {
            bottomNav.removeBadge(menuItemId);
        }
    }

    private void dispatchRefresh(String eventType) {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (f instanceof RealtimeRefreshable) {
            ((RealtimeRefreshable) f).onRealtimeRefresh(eventType);
        }
        if (eventType == null) {
            return;
        }
        if (eventType.startsWith("friend.") || "conversation.created".equals(eventType)
                || "conversation.member_changed".equals(eventType)) {
            friendRepo.syncFriends(() -> runOnUiThread(this::refreshNavBadges));
        }
        if (eventType.startsWith("msg.") || eventType.startsWith("conversation.")) {
            chatRepo.syncConversations(() -> runOnUiThread(this::refreshNavBadges));
        }
        if (eventType.startsWith("social.")) {
            socialRepo.syncFeed(() -> runOnUiThread(this::refreshNavBadges));
        }
    }

    private void updateConnectionBanner(WsChatManager ws) {
        if (wsBanner == null) {
            return;
        }
        WsChatManager.State state = ws.getState();
        if (state == WsChatManager.State.AUTHENTICATED && ws.isHeartbeatHealthy()) {
            wsBanner.setVisibility(View.GONE);
        } else {
            wsBanner.setVisibility(View.VISIBLE);
            if (state == WsChatManager.State.CONNECTING) {
                tvWsStatus.setText(R.string.ws_connecting);
            } else {
                tvWsStatus.setText(R.string.ws_service_disconnected);
            }
        }
    }

    @Override
    public void onStateChanged(WsChatManager.State state) {
        runOnUiThread(() -> updateConnectionBanner(FlzChatApp.get().getWsChatManager()));
    }

    @Override
    public void onHeartbeatChanged(boolean healthy) {
        runOnUiThread(() -> updateConnectionBanner(FlzChatApp.get().getWsChatManager()));
    }

    @Override
    public void onMessageEvent(com.flz.flz_chat.data.local.entity.MessageEntity entity) {
        chatRepo.syncConversations(() -> runOnUiThread(() -> {
            dispatchRefresh("msg.new");
            refreshNavBadges();
        }));
    }

    @Override
    public void onSendResult(String clientMsgId, int code, long messageId) {
    }

    @Override
    public void onRealtimeEvent(String eventType) {
        runOnUiThread(() -> {
            dispatchRefresh(eventType);
            refreshNavBadges();
        });
    }

    @Override
    public void onKicked(String reason) {
        runOnUiThread(() -> {
            Toast.makeText(this, "连接被踢下线: " + reason, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
