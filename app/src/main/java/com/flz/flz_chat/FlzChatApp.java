package com.flz.flz_chat;

import android.app.Application;

import com.flz.flz_chat.data.local.AppDatabase;
import com.flz.flz_chat.data.remote.WsChatManager;
import com.flz.flz_chat.session.SessionManager;

/**
 * 应用入口：初始化本地数据库、会话管理与 WebSocket 管理器单例。
 */
public class FlzChatApp extends Application {

    private static FlzChatApp instance;
    private AppDatabase database;
    private SessionManager sessionManager;
    private WsChatManager wsChatManager;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        database = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);
        wsChatManager = new WsChatManager(sessionManager);
    }

    public static FlzChatApp get() {
        return instance;
    }

    public AppDatabase getDatabase() {
        return database;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public WsChatManager getWsChatManager() {
        return wsChatManager;
    }
}
