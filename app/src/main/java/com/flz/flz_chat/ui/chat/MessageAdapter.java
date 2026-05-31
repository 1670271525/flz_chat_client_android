package com.flz.flz_chat.ui.chat;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.MessageEntity;
import com.flz.flz_chat.data.repository.FileRepository;
import com.flz.flz_chat.util.ApiCallback;
import com.flz.flz_chat.util.AvatarHelper;
import com.flz.flz_chat.util.ImageLoader;
import com.flz.flz_chat.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息列表：整体按时间排序，每行根据 senderId 与当前用户 id 比较决定左右布局。
 */
class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {

    private static final long GAP_MS = 5 * 60 * 1000L;

    interface AvatarProvider {
        String selfAvatarUrl();
        String peerAvatarUrl();
    }

    private final List<MessageEntity> data = new ArrayList<>();
    private final FileRepository fileRepo = new FileRepository();
    private final Map<String, String> urlCache = new HashMap<>();
    private long myUserId;
    private AvatarProvider avatarProvider;

    void setMyUserId(long myUserId) {
        this.myUserId = myUserId;
    }

    void setAvatarProvider(AvatarProvider provider) {
        this.avatarProvider = provider;
    }

    void setData(List<MessageEntity> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MessageEntity message = data.get(position);
        boolean isMine = message.senderId == myUserId;
        bindRowLayout(h, isMine);
        bindMessageContent(h, message, isMine);
        bindTimeDivider(h, position, message);
    }

    /** 根据收发方 id：自己 → 右对齐（气泡在左、头像在右）；对方 → 左对齐（头像在左、气泡在右） */
    private void bindRowLayout(VH h, boolean isMine) {
        FrameLayout rowHost = (FrameLayout) h.row.getParent();
        FrameLayout.LayoutParams rowLp = (FrameLayout.LayoutParams) h.row.getLayoutParams();
        if (rowLp == null) {
            rowLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        rowLp.gravity = isMine ? Gravity.END : Gravity.START;
        h.row.setLayoutParams(rowLp);

        h.row.removeAllViews();
        if (isMine) {
            h.row.addView(h.bubbleContainer);
            h.row.addView(h.ivAvatar);
        } else {
            h.row.addView(h.ivAvatar);
            h.row.addView(h.bubbleContainer);
        }
    }

    private void bindMessageContent(VH h, MessageEntity message, boolean isMine) {
        boolean isImage = message.type == 2;
        h.tvContent.setVisibility(isImage ? View.GONE : View.VISIBLE);
        h.ivImage.setVisibility(isImage ? View.VISIBLE : View.GONE);

        if (isImage) {
            bindImage(h.ivImage, message);
        } else {
            h.tvContent.setText(displayText(message));
            h.tvContent.setAlpha("pending".equals(message.status) ? 0.6f : 1f);
            h.tvContent.setBackgroundResource(isMine
                    ? R.drawable.bg_bubble_self : R.drawable.bg_bubble_other);
            h.tvContent.setTextColor(h.tvContent.getContext().getColor(isMine
                    ? R.color.text_on_primary : R.color.text_primary));
        }

        String avatarUrl = isMine
                ? (avatarProvider != null ? avatarProvider.selfAvatarUrl() : null)
                : (avatarProvider != null ? avatarProvider.peerAvatarUrl() : null);
        AvatarHelper.load(h.ivAvatar, avatarUrl);
    }

    private void bindTimeDivider(VH h, int position, MessageEntity message) {
        long currentTs = TimeUtil.parseToMillis(message.createdAt);
        boolean showTime = position == 0;
        if (position > 0) {
            long prevTs = TimeUtil.parseToMillis(data.get(position - 1).createdAt);
            if (currentTs > 0 && prevTs > 0 && currentTs - prevTs > GAP_MS) {
                showTime = true;
            }
        }
        if (showTime && currentTs > 0) {
            h.tvTimeDivider.setVisibility(View.VISIBLE);
            h.tvTimeDivider.setText(TimeUtil.formatChatGap(currentTs));
        } else {
            h.tvTimeDivider.setVisibility(View.GONE);
        }
    }

    private String displayText(MessageEntity message) {
        if ("failed".equals(message.status)) {
            return message.content + " (发送失败)";
        }
        if (message.type == 2) {
            return "[图片]";
        }
        return message.content;
    }

    private void bindImage(ImageView target, MessageEntity message) {
        String objectKey = message.content;
        if (objectKey == null || objectKey.trim().isEmpty()) {
            target.setImageResource(R.drawable.bg_avatar_placeholder);
            return;
        }
        if (objectKey.startsWith("http")) {
            ImageLoader.load(target, objectKey);
            return;
        }
        String cached = urlCache.get(objectKey);
        if (cached != null) {
            ImageLoader.load(target, cached);
            return;
        }
        target.setImageResource(R.drawable.bg_avatar_placeholder);
        fileRepo.resolveDownloadUrl(objectKey, new ApiCallback<String>() {
            @Override
            public void onSuccess(String data) {
                urlCache.put(objectKey, data);
                ImageLoader.load(target, data);
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        LinearLayout row;
        FrameLayout bubbleContainer;
        TextView tvContent, tvTimeDivider;
        ImageView ivAvatar, ivImage;

        VH(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.row);
            bubbleContainer = itemView.findViewById(R.id.bubbleContainer);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTimeDivider = itemView.findViewById(R.id.tvTimeDivider);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            ivImage = itemView.findViewById(R.id.ivImage);
        }
    }
}
