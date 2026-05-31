package com.flz.flz_chat.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.FriendEntity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.VH> {

    interface Listener {
        void onChat(FriendEntity friend);
        void onDetail(FriendEntity friend);
    }

    private final List<FriendEntity> data = new ArrayList<>();
    private final Listener listener;

    FriendAdapter(Listener listener) {
        this.listener = listener;
    }

    void setData(List<FriendEntity> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FriendEntity f = data.get(position);
        String name = f.alias != null && !f.alias.isEmpty() ? f.alias : f.nickname;
        h.tvName.setText(name);
        h.tvSub.setText(f.signature != null ? f.signature : "点击查看详情");
        h.btnAction.setText("聊天");
        h.btnAction.setOnClickListener(v -> listener.onChat(f));
        h.itemView.setOnClickListener(v -> listener.onDetail(f));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvSub;
        MaterialButton btnAction;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvSub = itemView.findViewById(R.id.tvSub);
            btnAction = itemView.findViewById(R.id.btnAction);
        }
    }
}
