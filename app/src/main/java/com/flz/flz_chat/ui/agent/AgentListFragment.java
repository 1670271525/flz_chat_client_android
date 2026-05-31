package com.flz.flz_chat.ui.agent;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.AgentSessionEntity;
import com.flz.flz_chat.data.repository.AgentRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * 智能体 Tab：会话列表，对接 flz_agent SSE。
 */
public class AgentListFragment extends Fragment {

    private final AgentRepository repo = new AgentRepository();
    private AgentSessionAdapter adapter;
    private TextView tvAgentStatus;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agent_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getActivity() != null && !AuthGuard.isLoggedIn(getActivity())) return;

        tvAgentStatus = view.findViewById(R.id.tvAgentStatus);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        RecyclerView recycler = view.findViewById(R.id.recycler);
        MaterialButton btnNew = view.findViewById(R.id.btnNewSession);

        adapter = new AgentSessionAdapter(session -> {
            Intent i = new Intent(getActivity(), AgentChatActivity.class);
            i.putExtra(AgentChatActivity.EXTRA_SESSION_ID, session.sessionId);
            i.putExtra(AgentChatActivity.EXTRA_TITLE, session.title);
            startActivity(i);
        });
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        btnNew.setOnClickListener(v -> {
            Intent i = new Intent(getActivity(), AgentChatActivity.class);
            i.putExtra(AgentChatActivity.EXTRA_SESSION_ID, 0L);
            i.putExtra(AgentChatActivity.EXTRA_TITLE, "新对话");
            startActivity(i);
        });

        checkAgentHealth();
        loadSessions();
    }

    private void checkAgentHealth() {
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean ok = repo.checkHealth();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (tvAgentStatus != null) {
                        tvAgentStatus.setText(ok
                                ? "flz_agent 已就绪 · SSE 流式"
                                : "flz_agent 未连接 · 将使用离线说明");
                        tvAgentStatus.setTextColor(androidx.core.content.ContextCompat.getColor(
                                requireContext(), ok ? R.color.accent : R.color.accent_warm));
                    }
                    if (!ok) {
                        Toast.makeText(getContext(),
                                "请启动 flz_agent (8090)", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void loadSessions() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<AgentSessionEntity> list = repo.getSessions();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.setData(list);
                    if (tvEmpty != null) {
                        tvEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            loadSessions();
            checkAgentHealth();
        }
    }
}
