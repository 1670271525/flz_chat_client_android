package com.flz.flz_chat.data.repository;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.data.remote.ApiService;
import com.flz.flz_chat.data.remote.RetrofitClient;
import com.flz.flz_chat.data.remote.dto.AuthDtos;
import com.flz.flz_chat.util.ApiCallback;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthRepository {

    private final ApiService api = RetrofitClient.getApi();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public void login(String account, String password, ApiCallback<AuthDtos.TokenResponse> cb) {
        api.login(new AuthDtos.LoginRequest(account, password)).enqueue(cb);
    }

    public void register(AuthDtos.RegisterRequest req, ApiCallback<AuthDtos.TokenResponse> cb) {
        api.register(req).enqueue(cb);
    }

    public void sendEmailCode(String email, ApiCallback<Void> cb) {
        api.sendEmailCode(new AuthDtos.EmailCodeRequest(email, "REGISTER")).enqueue(cb);
    }

    public void logout(Runnable onDone) {
        api.logout().enqueue(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                FlzChatApp.get().getWsChatManager().disconnect();
                long ownerUserId = FlzChatApp.get().getSessionManager().getUserId();
                clearLocalCache(ownerUserId);
                FlzChatApp.get().getSessionManager().clear();
                onDone.run();
            }

            @Override
            public void onError(String message) {
                FlzChatApp.get().getWsChatManager().disconnect();
                long ownerUserId = FlzChatApp.get().getSessionManager().getUserId();
                clearLocalCache(ownerUserId);
                FlzChatApp.get().getSessionManager().clear();
                onDone.run();
            }
        });
    }

    public void persistToken(AuthDtos.TokenResponse data, String userName) {
        FlzChatApp.get().getSessionManager().saveLogin(
                data.userId,
                userName != null ? userName : String.valueOf(data.userId),
                data.token,
                data.refreshToken
        );
        clearLocalCache(0);
    }

    private void clearLocalCache(long ownerUserId) {
        io.execute(() -> {
            ChatRepository chatRepository = new ChatRepository();
            FriendRepository friendRepository = new FriendRepository();
            SocialRepository socialRepository = new SocialRepository();
            AgentRepository agentRepository = new AgentRepository();
            chatRepository.clearLocalCacheForOwner(ownerUserId);
            friendRepository.clearLocalCacheForOwner(ownerUserId);
            socialRepository.clearLocalCacheForOwner(ownerUserId);
            agentRepository.clearLocalCacheForOwner(ownerUserId);
        });
    }
}
