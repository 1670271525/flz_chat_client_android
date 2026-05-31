package com.flz.flz_chat.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.ui.auth.LoginActivity;

/**
 * 登录守卫：所有业务 Activity 启动前检查 token，未登录跳转登录页。
 */
public final class AuthGuard {

    private AuthGuard() {}

    public static boolean requireLogin(Activity activity) {
        if (FlzChatApp.get().getSessionManager().isLoggedIn()) {
            return true;
        }
        activity.startActivity(new Intent(activity, LoginActivity.class));
        activity.finish();
        return false;
    }

    public static boolean isLoggedIn(Context context) {
        return FlzChatApp.get().getSessionManager().isLoggedIn();
    }
}
