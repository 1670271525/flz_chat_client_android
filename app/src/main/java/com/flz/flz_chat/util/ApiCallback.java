package com.flz.flz_chat.util;

import androidx.annotation.Nullable;

import com.flz.flz_chat.data.remote.dto.ApiResult;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public abstract class ApiCallback<T> implements Callback<ApiResult<T>> {

    @Override
    public void onResponse(Call<ApiResult<T>> call, Response<ApiResult<T>> response) {
        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
            onSuccess(response.body().data);
        } else {
            String msg = "请求失败";
            if (response.body() != null && response.body().message != null) {
                msg = response.body().message;
            }
            onError(msg);
        }
    }

    @Override
    public void onFailure(Call<ApiResult<T>> call, Throwable t) {
        onError(t.getMessage() != null ? t.getMessage() : "网络异常");
    }

    public abstract void onSuccess(@Nullable T data);

    public abstract void onError(String message);
}
