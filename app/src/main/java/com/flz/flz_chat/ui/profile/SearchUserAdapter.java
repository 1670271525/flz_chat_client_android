package com.flz.flz_chat.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.remote.dto.UserDtos;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

class SearchUserAdapter extends RecyclerView.Adapter<SearchUserAdapter.VH> {

    interface OnAdd { void onAdd(UserDtos.UserBrief user); }

    private final List<UserDtos.UserBrief> data = new ArrayList<>();
    private final OnAdd onAdd;

    SearchUserAdapter(OnAdd onAdd) {
        this.onAdd = onAdd;
    }

    void setData(List<UserDtos.UserBrief> list) {
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
        UserDtos.UserBrief u = data.get(position);
        String name = u.userName;
        if (u.information != null && u.information.nickname != null) {
            name = u.information.nickname;
        }
        h.tvName.setText(name);
        h.tvSub.setText("ID: " + u.userId);
        h.btnAction.setText("加好友");
        h.btnAction.setOnClickListener(v -> onAdd.onAdd(u));
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
