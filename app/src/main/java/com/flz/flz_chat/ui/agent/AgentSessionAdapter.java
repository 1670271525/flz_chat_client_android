package com.flz.flz_chat.ui.agent;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.AgentSessionEntity;

import java.util.ArrayList;
import java.util.List;

class AgentSessionAdapter extends RecyclerView.Adapter<AgentSessionAdapter.VH> {

    interface OnClick { void onClick(AgentSessionEntity s); }

    private final List<AgentSessionEntity> data = new ArrayList<>();
    private final OnClick onClick;

    AgentSessionAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    void setData(List<AgentSessionEntity> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_agent_session, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AgentSessionEntity s = data.get(position);
        h.tvTitle.setText(s.title != null ? s.title : "对话");
        String type = s.agentType != null ? s.agentType : "chat";
        h.tvType.setText(typeLabel(type));
        h.itemView.setOnClickListener(v -> onClick.onClick(s));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private static String typeLabel(String type) {
        switch (type) {
            case "code": return "代码助手";
            case "draw": return "绘图助手";
            case "mcp": return "MCP";
            default: return "通用对话";
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvType;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvType = itemView.findViewById(R.id.tvType);
        }
    }
}
