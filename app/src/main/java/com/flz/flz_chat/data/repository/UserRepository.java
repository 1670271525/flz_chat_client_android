package com.flz.flz_chat.data.repository;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.data.remote.ApiService;
import com.flz.flz_chat.data.remote.RetrofitClient;
import com.flz.flz_chat.data.remote.dto.UserDtos;
import com.flz.flz_chat.util.ApiCallback;

public class UserRepository {

    private final ApiService api = RetrofitClient.getApi();

    public void fetchMe(ApiCallback<UserDtos.UserMe> cb) {
        api.getMe().enqueue(new ApiCallback<UserDtos.UserMe>() {
            @Override
            public void onSuccess(UserDtos.UserMe data) {
                if (data != null && data.information != null && data.information.nickname != null) {
                    FlzChatApp.get().getSessionManager().saveProfile(data.information.nickname);
                }
                cb.onSuccess(data);
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public void updateMe(UserDtos.UpdateMeRequest req, ApiCallback<UserDtos.UserMe> cb) {
        api.updateMe(req).enqueue(cb);
    }

    public void search(String keyword, ApiCallback<com.flz.flz_chat.data.remote.dto.PageResult<UserDtos.UserBrief>> cb) {
        api.searchUsers(keyword, 1, 20).enqueue(cb);
    }

    public void fetchUser(long userId, ApiCallback<UserDtos.UserBrief> cb) {
        api.getUser(userId).enqueue(cb);
    }
}
