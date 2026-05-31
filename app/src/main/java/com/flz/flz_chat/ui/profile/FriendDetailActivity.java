package com.flz.flz_chat.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.remote.dto.UserDtos;
import com.flz.flz_chat.data.repository.ChatRepository;
import com.flz.flz_chat.data.repository.FriendRepository;
import com.flz.flz_chat.data.repository.UserRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.ui.chat.ChatActivity;
import com.flz.flz_chat.util.ApiCallback;
import com.flz.flz_chat.util.AvatarHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/**
 * 好友详情：查看资料、发起聊天、拉黑、删除。
 */
public class FriendDetailActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "userId";
    public static final String EXTRA_NICKNAME = "nickname";
    public static final String EXTRA_ALIAS = "alias";
    public static final String EXTRA_AVATAR = "avatarUrl";
    public static final String EXTRA_SIGNATURE = "signature";

    private static final String[] GENDER_LABELS = {"未知", "男", "女"};

    private final FriendRepository friendRepo = new FriendRepository();
    private final ChatRepository chatRepo = new ChatRepository();
    private final UserRepository userRepo = new UserRepository();
    private long userId;
    private String displayName;
    private String avatarUrl;

    private ImageView ivAvatar;
    private TextView tvName;
    private TextView tvSignature;
    private TextView tvGender;
    private TextView tvBirthday;
    private TextView tvMood;
    private TextView tvRegion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireLogin(this)) return;
        setContentView(R.layout.activity_friend_detail);

        userId = getIntent().getLongExtra(EXTRA_USER_ID, 0);
        String nickname = getIntent().getStringExtra(EXTRA_NICKNAME);
        String alias = getIntent().getStringExtra(EXTRA_ALIAS);
        avatarUrl = getIntent().getStringExtra(EXTRA_AVATAR);
        String signature = getIntent().getStringExtra(EXTRA_SIGNATURE);
        displayName = alias != null && !alias.isEmpty() ? alias : nickname;

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("好友详情");
        toolbar.setNavigationOnClickListener(v -> finish());

        ivAvatar = findViewById(R.id.ivAvatar);
        tvName = findViewById(R.id.tvName);
        tvSignature = findViewById(R.id.tvSignature);
        tvGender = findViewById(R.id.tvGender);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvMood = findViewById(R.id.tvMood);
        tvRegion = findViewById(R.id.tvRegion);
        MaterialButton btnChat = findViewById(R.id.btnChat);
        MaterialButton btnBlock = findViewById(R.id.btnBlock);
        MaterialButton btnDelete = findViewById(R.id.btnDelete);

        showCachedProfile(signature);

        btnChat.setOnClickListener(v -> chatRepo.createSingleChat(userId, new ApiCallback<Long>() {
            @Override
            public void onSuccess(Long conversationId) {
                runOnUiThread(() -> {
                    Intent i = new Intent(FriendDetailActivity.this, ChatActivity.class);
                    i.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId);
                    i.putExtra(ChatActivity.EXTRA_TITLE, displayName);
                    i.putExtra(ChatActivity.EXTRA_PEER_AVATAR, avatarUrl);
                    startActivity(i);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(FriendDetailActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        }));

        btnBlock.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("拉黑好友")
                .setMessage("确定拉黑该好友吗？")
                .setPositiveButton("确定", (d, w) -> friendRepo.block(userId, wrapAction("已拉黑")))
                .setNegativeButton("取消", null)
                .show());

        btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("删除好友")
                .setMessage("确定删除该好友吗？")
                .setPositiveButton("确定", (d, w) -> friendRepo.delete(userId, wrapAction("已删除好友")))
                .setNegativeButton("取消", null)
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile();
    }

    private void showCachedProfile(String signature) {
        tvName.setText(displayName != null ? displayName : ("用户" + userId));
        tvSignature.setText(signature != null && !signature.isEmpty() ? signature : "暂无签名");
        AvatarHelper.load(ivAvatar, avatarUrl);
        bindProfileField(tvGender, "性别", "未知");
        bindProfileField(tvBirthday, "生日", "未填写");
        bindProfileField(tvMood, "状态", "未填写");
        bindProfileField(tvRegion, "地区", "未填写");
    }

    private void loadUserProfile() {
        userRepo.fetchUser(userId, new ApiCallback<UserDtos.UserBrief>() {
            @Override
            public void onSuccess(UserDtos.UserBrief data) {
                if (data == null) return;
                runOnUiThread(() -> applyProfile(data));
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void applyProfile(UserDtos.UserBrief data) {
        UserDtos.Information info = data.resolvedInformation();
        if (info.nickname != null && !info.nickname.isEmpty()) {
            displayName = info.nickname;
            tvName.setText(displayName);
        } else if (data.userName != null && !data.userName.isEmpty()) {
            displayName = data.userName;
            tvName.setText(displayName);
        }
        if (info.signature != null && !info.signature.isEmpty()) {
            tvSignature.setText(info.signature);
        }
        if (info.avatarUrl != null && !info.avatarUrl.isEmpty()) {
            avatarUrl = info.avatarUrl;
            AvatarHelper.load(ivAvatar, avatarUrl);
        }
        bindProfileField(tvGender, "性别", formatGender(info.gender));
        bindProfileField(tvBirthday, "生日", emptyToDefault(info.birthday, "未填写"));
        bindProfileField(tvMood, "状态", emptyToDefault(info.mood, "未填写"));
        bindProfileField(tvRegion, "地区", emptyToDefault(info.region, "未填写"));
    }

    private static void bindProfileField(TextView tv, String label, String value) {
        tv.setText(label + "：" + value);
    }

    private static String formatGender(Integer gender) {
        if (gender == null || gender < 0 || gender >= GENDER_LABELS.length) {
            return "未知";
        }
        return GENDER_LABELS[gender];
    }

    private static String emptyToDefault(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    private ApiCallback<Void> wrapAction(String successMsg) {
        return new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                runOnUiThread(() -> {
                    Toast.makeText(FriendDetailActivity.this, successMsg, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(FriendDetailActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        };
    }
}
