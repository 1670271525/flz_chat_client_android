package com.flz.flz_chat.ui.agent;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.AgentMessageEntity;
import com.flz.flz_chat.data.local.entity.AgentSessionEntity;
import com.flz.flz_chat.data.repository.AgentRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 智能体对话：对接 flz_agent POST /v1/chat/sse，流式刷新 UI。
 */
public class AgentChatActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "sessionId";
    public static final String EXTRA_TITLE = "title";

    private final AgentRepository repo = new AgentRepository();
    private long sessionId;
    private AgentMessageAdapter adapter;
    private boolean isStreaming;
    private MaterialButton btnSend;
    private TextView tvToolHint;
    private ChipGroup chipGroup;
    private MaterialToolbar toolbar;
    private String pendingAgentType = "chat";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireLogin(this)) return;
        setContentView(R.layout.activity_agent_chat);

        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, 0);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(title != null ? title : (sessionId > 0 ? "智能体" : "新对话"));
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recycler);
        EditText etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);
        tvToolHint = findViewById(R.id.tvToolHint);
        chipGroup = findViewById(R.id.chipAgentType);

        adapter = new AgentMessageAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        setupAgentTypeChips();
        loadMessages();

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
            if (text.isEmpty() || isStreaming) return;
            etInput.setText("");
            setStreaming(true);
            tvToolHint.setVisibility(View.GONE);
            repo.sendMessageCreatingSessionIfNeeded(sessionId, pendingAgentType, text, streamListener(recycler));
        });
    }

    private void setupAgentTypeChips() {
        if (sessionId <= 0) {
            selectChip(pendingAgentType);
        } else {
            Executors.newSingleThreadExecutor().execute(() -> {
                AgentSessionEntity s = repo.getSession(sessionId);
                if (s == null) return;
                String type = s.agentType != null ? s.agentType : "chat";
                pendingAgentType = type;
                runOnUiThread(() -> selectChip(type));
            });
        }

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty() || isStreaming) return;
            String type = chipIdToType(checkedIds.get(0));
            pendingAgentType = type;
            if (sessionId > 0) {
                repo.updateAgentType(sessionId, type);
            }
        });
    }

    private void selectChip(String agentType) {
        int id = R.id.chipChat;
        if ("code".equals(agentType)) id = R.id.chipCode;
        else if ("draw".equals(agentType)) id = R.id.chipDraw;
        else if ("mcp".equals(agentType)) id = R.id.chipMcp;
        Chip chip = findViewById(id);
        if (chip != null) chip.setChecked(true);
    }

    private String chipIdToType(int chipId) {
        if (chipId == R.id.chipCode) return "code";
        if (chipId == R.id.chipDraw) return "draw";
        if (chipId == R.id.chipMcp) return "mcp";
        return "chat";
    }

    private AgentRepository.StreamListener streamListener(RecyclerView recycler) {
        return new AgentRepository.StreamListener() {
            @Override
            public void onSessionReady(long resolvedSessionId, String title) {
                if (sessionId <= 0 && resolvedSessionId > 0) {
                    sessionId = resolvedSessionId;
                }
                if (title != null && !title.trim().isEmpty() && toolbar != null) {
                    toolbar.setTitle(title);
                }
            }

            @Override
            public void onMessagesChanged() {
                loadMessages();
            }

            @Override
            public void onStreamingDelta(long assistantMsgId, String fullText) {
                adapter.updateStreamingContent(assistantMsgId, fullText);
                scrollToEnd(recycler);
            }

            @Override
            public void onToolStatus(String hint) {
                tvToolHint.setText(hint);
                tvToolHint.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFinished() {
                setStreaming(false);
                tvToolHint.setVisibility(View.GONE);
                loadMessages();
            }

            @Override
            public void onError(String message) {
                setStreaming(false);
                Toast.makeText(AgentChatActivity.this, message, Toast.LENGTH_SHORT).show();
                loadMessages();
            }
        };
    }

    private void setStreaming(boolean streaming) {
        isStreaming = streaming;
        btnSend.setEnabled(!streaming);
        btnSend.setAlpha(streaming ? 0.5f : 1f);
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            chipGroup.getChildAt(i).setEnabled(!streaming);
        }
    }

    private void loadMessages() {
        if (sessionId <= 0) {
            runOnUiThread(() -> adapter.setData(new ArrayList<>()));
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            List<AgentMessageEntity> list = repo.getMessages(sessionId);
            runOnUiThread(() -> {
                adapter.setData(list);
                RecyclerView rv = findViewById(R.id.recycler);
                scrollToEnd(rv);
            });
        });
    }

    private void scrollToEnd(RecyclerView rv) {
        if (adapter.getItemCount() > 0) {
            rv.post(() -> rv.scrollToPosition(adapter.getItemCount() - 1));
        }
    }
}
