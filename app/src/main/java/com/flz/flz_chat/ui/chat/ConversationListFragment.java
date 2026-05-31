package com.flz.flz_chat.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.ConversationEntity;
import com.flz.flz_chat.data.repository.ChatRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.ui.RealtimeRefreshable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 聊天 Tab：展示会话列表（HTTP 同步 + SQLite 缓存）。
 */
public class ConversationListFragment extends Fragment implements RealtimeRefreshable {

    private final ChatRepository repo = new ChatRepository();
    private ConversationAdapter adapter;
    private SwipeRefreshLayout swipe;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_conversation_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getActivity() != null && !AuthGuard.isLoggedIn(getActivity())) {
            return;
        }
        swipe = view.findViewById(R.id.swipeRefresh);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        RecyclerView recycler = view.findViewById(R.id.recycler);
        adapter = new ConversationAdapter(item -> {
            Intent i = new Intent(getActivity(), ChatActivity.class);
            i.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, item.conversationId);
            i.putExtra(ChatActivity.EXTRA_TITLE, item.title != null ? item.title : "会话");
            i.putExtra(ChatActivity.EXTRA_PEER_AVATAR, item.avatarUrl);
            startActivity(i);
        });
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(this::refresh);
        refresh();
    }

    private void refresh() {
        swipe.setRefreshing(true);
        repo.syncConversations(() -> Executors.newSingleThreadExecutor().execute(() -> {
            List<ConversationEntity> list = repo.getLocalConversations();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                swipe.setRefreshing(false);
                adapter.setData(list);
                tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (swipe != null) {
            refresh();
        }
    }

    @Override
    public void onRealtimeRefresh(String eventType) {
        if (swipe != null && (eventType == null || eventType.startsWith("msg.")
                || eventType.startsWith("conversation."))) {
            refresh();
        }
    }
}
