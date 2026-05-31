package com.flz.flz_chat.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.R;
import com.flz.flz_chat.data.remote.dto.UserDtos;
import com.flz.flz_chat.data.repository.AuthRepository;
import com.flz.flz_chat.data.repository.UserRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.ui.auth.LoginActivity;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.button.MaterialButton;

/**
 * 我 Tab：资料展示、好友入口、登出。
 */
public class ProfileFragment extends Fragment {

    private final UserRepository userRepo = new UserRepository();
    private final AuthRepository authRepo = new AuthRepository();
    private TextView tvNickname;
    private TextView tvUserName;
    private TextView tvSignature;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getActivity() != null && !AuthGuard.isLoggedIn(getActivity())) return;

        tvNickname = view.findViewById(R.id.tvNickname);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvSignature = view.findViewById(R.id.tvSignature);
        MaterialButton btnEdit = view.findViewById(R.id.btnEdit);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);

        tvUserName.setText(FlzChatApp.get().getSessionManager().getUserName());
        String nick = FlzChatApp.get().getSessionManager().getNickname();
        tvNickname.setText(nick != null ? nick : "未设置昵称");
        tvSignature.setText("暂无签名");

        refreshProfile();

        btnEdit.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), EditProfileActivity.class)));
        btnLogout.setOnClickListener(v -> authRepo.logout(() -> {
            if (getActivity() != null) {
                getActivity().startActivity(new Intent(getActivity(), LoginActivity.class));
                getActivity().finish();
            }
        }));
    }

    private void refreshProfile() {
        userRepo.fetchMe(new ApiCallback<UserDtos.UserMe>() {
            @Override
            public void onSuccess(UserDtos.UserMe data) {
                if (getActivity() == null || data == null) return;
                getActivity().runOnUiThread(() -> {
                    if (data.information != null) {
                        if (data.information.nickname != null) {
                            tvNickname.setText(data.information.nickname);
                        }
                        tvSignature.setText(data.information.signature != null
                                ? data.information.signature : "暂无签名");
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null && AuthGuard.isLoggedIn(getActivity())) {
            refreshProfile();
        }
    }
}
