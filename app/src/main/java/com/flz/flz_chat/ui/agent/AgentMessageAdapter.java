package com.flz.flz_chat.ui.agent;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.AgentMessageEntity;

import java.util.ArrayList;
import java.util.List;

class AgentMessageAdapter extends RecyclerView.Adapter<AgentMessageAdapter.VH> {

    private final List<AgentMessageEntity> data = new ArrayList<>();

    void setData(List<AgentMessageEntity> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    void updateStreamingContent(long msgId, String content) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).id == msgId) {
                data.get(i).content = content;
                data.get(i).status = "streaming";
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_agent_message, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AgentMessageEntity m = data.get(position);
        boolean user = "user".equals(m.role);
        String text = m.content != null ? m.content : "";
        if ("streaming".equals(m.status)) {
            text = text + " ▌";
        }
        h.tvContent.setText(text);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.tvContent.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        lp.gravity = user ? Gravity.END : Gravity.START;
        h.tvContent.setLayoutParams(lp);

        if (user) {
            h.tvContent.setBackgroundResource(R.drawable.bg_bubble_self);
            h.tvContent.setTextColor(h.itemView.getContext().getColor(R.color.text_on_primary));
        } else if ("error".equals(m.status)) {
            h.tvContent.setBackgroundResource(R.drawable.bg_bubble_other);
            h.tvContent.setTextColor(h.itemView.getContext().getColor(R.color.error));
        } else {
            h.tvContent.setBackgroundResource(R.drawable.bg_bubble_agent);
            h.tvContent.setTextColor(h.itemView.getContext().getColor(R.color.text_primary));
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvContent;

        VH(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvContent);
        }
    }
}
