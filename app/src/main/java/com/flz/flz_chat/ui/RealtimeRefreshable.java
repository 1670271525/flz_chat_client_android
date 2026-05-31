package com.flz.flz_chat.ui;

/**
 * 长连接推送后由 MainActivity 转发，触发各 Tab 刷新。
 */
public interface RealtimeRefreshable {

    void onRealtimeRefresh(String eventType);
}
