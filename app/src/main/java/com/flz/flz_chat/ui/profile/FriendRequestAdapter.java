package com.flz.flz_chat.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.remote.dto.ChatDtos;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.VH> {

    interface Listener {
        void onAccept(ChatDtos.FriendRequestItem item, Runnable onComplete);
        void onReject(ChatDtos.FriendRequestItem item, Runnable onComplete);
    }

    private final List<ChatDtos.FriendRequestItem> data = new ArrayList<>();
    private final Set<Long> loadingIds = new HashSet<>();
    private final Listener listener;

    FriendRequestAdapter(Listener listener) {
        this.listener = listener;
    }

    void setData(List<ChatDtos.FriendRequestItem> list) {
        data.clear();
        loadingIds.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_request, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ChatDtos.FriendRequestItem item = data.get(position);
        long requestId = item.resolvedRequestId();
        String displayName = item.fromNickname != null && !item.fromNickname.trim().isEmpty()
                ? item.fromNickname : String.valueOf(item.fromUserId);
        h.tvName.setText(displayName);
        h.tvSub.setText(item.remark != null && !item.remark.trim().isEmpty()
                ? item.remark : "等待你处理这条好友申请");

        boolean loading = loadingIds.contains(requestId);
        h.btnAccept.setEnabled(!loading);
        h.btnReject.setEnabled(!loading);
        h.btnAccept.setText(loading ? "处理中..." : "同意");
        h.btnReject.setText(loading ? "请稍候" : "拒绝");

        h.btnAccept.setOnClickListener(v -> {
            if (loadingIds.contains(requestId)) return;
            setLoading(requestId, true);
            listener.onAccept(item, () -> setLoading(requestId, false));
        });
        h.btnReject.setOnClickListener(v -> {
            if (loadingIds.contains(requestId)) return;
            setLoading(requestId, true);
            listener.onReject(item, () -> setLoading(requestId, false));
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private void setLoading(long requestId, boolean loading) {
        if (loading) {
            loadingIds.add(requestId);
        } else {
            loadingIds.remove(requestId);
        }
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvSub;
        MaterialButton btnAccept, btnReject;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvSub = itemView.findViewById(R.id.tvSub);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
        }
    }
}
