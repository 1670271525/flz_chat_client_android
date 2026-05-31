package com.flz.flz_chat.ui.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.SocialEntity;
import com.flz.flz_chat.util.AvatarHelper;
import com.flz.flz_chat.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

class SocialAdapter extends RecyclerView.Adapter<SocialAdapter.VH> {

    interface OnLike {
        void onLike(SocialEntity post, int position);
    }

    private final List<SocialEntity> data = new ArrayList<>();
    private final long myUserId;
    private final OnLike onLike;

    SocialAdapter(long myUserId, OnLike onLike) {
        this.myUserId = myUserId;
        this.onLike = onLike;
    }

    void setData(List<SocialEntity> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    void updatePost(int position, SocialEntity post) {
        if (position < 0 || position >= data.size()) {
            return;
        }
        data.set(position, post);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_social, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SocialEntity p = data.get(position);
        boolean isMine = p.userId == myUserId;
        h.tvAuthor.setText(p.nickname != null ? p.nickname : "用户" + p.userId);
        h.tvMineTag.setVisibility(isMine ? View.VISIBLE : View.GONE);
        h.tvContent.setText(p.content);
        h.tvTime.setText(TimeUtil.formatRelative(p.createdAt));
        AvatarHelper.load(h.ivAvatar, p.avatarUrl);

        if (p.liked) {
            h.tvLike.setText("♥ " + p.likeCount);
            h.tvLike.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.like_active));
        } else {
            h.tvLike.setText("♡ " + p.likeCount);
            h.tvLike.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.text_secondary));
        }
        h.tvLike.setOnClickListener(v -> onLike.onLike(p, position));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvAuthor, tvMineTag, tvContent, tvTime, tvLike;

        VH(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvAuthor = itemView.findViewById(R.id.tvAuthor);
            tvMineTag = itemView.findViewById(R.id.tvMineTag);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvLike = itemView.findViewById(R.id.tvLike);
        }
    }
}
