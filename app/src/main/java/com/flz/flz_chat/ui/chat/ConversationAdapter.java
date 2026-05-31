package com.flz.flz_chat.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.ConversationEntity;
import com.flz.flz_chat.util.AvatarHelper;

import java.util.ArrayList;
import java.util.List;

class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {

    interface OnClick { void onClick(ConversationEntity item); }

    private final List<ConversationEntity> data = new ArrayList<>();
    private final OnClick onClick;

    ConversationAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    void setData(List<ConversationEntity> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ConversationEntity e = data.get(position);
        h.tvTitle.setText(e.title != null ? e.title : "会话");
        h.tvPreview.setText(e.lastPreview != null ? e.lastPreview : "");
        AvatarHelper.load(h.ivAvatar, e.avatarUrl);
        if (e.unreadCount > 0) {
            h.tvUnread.setVisibility(View.VISIBLE);
            h.tvUnread.setText(String.valueOf(e.unreadCount));
        } else {
            h.tvUnread.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> onClick.onClick(e));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvTitle, tvPreview, tvUnread;

        VH(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvPreview = itemView.findViewById(R.id.tvPreview);
            tvUnread = itemView.findViewById(R.id.tvUnread);
        }
    }
}
