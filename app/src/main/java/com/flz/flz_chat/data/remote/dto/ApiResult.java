package com.flz.flz_chat.data.remote.dto;

/**
 * 业务 HTTP 统一响应包装，code=20000 表示成功。
 */
public class ApiResult<T> {
    public int code;
    public String message;
    public T data;

    public boolean isSuccess() {
        return code == 20000;
    }
}
