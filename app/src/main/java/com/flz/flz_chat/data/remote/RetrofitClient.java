package com.flz.flz_chat.data.remote;

import com.flz.flz_chat.BuildConfig;
import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.data.remote.dto.ApiResult;
import com.flz.flz_chat.data.remote.dto.AuthDtos;
import com.flz.flz_chat.session.SessionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit 单例：自动附加 Bearer Token，401 时尝试 refresh。
 */
public final class RetrofitClient {

    private static volatile ApiService apiService;

    private RetrofitClient() {}

    public static ApiService getApi() {
        if (apiService == null) {
            synchronized (RetrofitClient.class) {
                if (apiService == null) {
                    apiService = buildRetrofit().create(ApiService.class);
                }
            }
        }
        return apiService;
    }

    private static Retrofit buildRetrofit() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(log)
                .build();

        return new Retrofit.Builder()
                .baseUrl(ensureTrailingSlash(BuildConfig.BUSINESS_BASE_URL))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private static String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private static class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            SessionManager session = FlzChatApp.get().getSessionManager();
            Request original = chain.request();
            String token = session.getToken();
            Request.Builder builder = original.newBuilder();
            if (token != null && !original.url().encodedPath().contains("/api/auth/")) {
                builder.header("Authorization", "Bearer " + token);
            }
            Response response = chain.proceed(builder.build());
            if (response.code() == 401 && session.getRefreshToken() != null) {
                response.close();
                String newToken = tryRefresh(session);
                if (newToken != null) {
                    Request retry = original.newBuilder()
                            .header("Authorization", "Bearer " + newToken)
                            .build();
                    return chain.proceed(retry);
                }
            }
            return response;
        }

        private String tryRefresh(SessionManager session) {
            try {
                retrofit2.Response<ApiResult<AuthDtos.TokenResponse>> resp =
                        new Retrofit.Builder()
                                .baseUrl(ensureTrailingSlash(BuildConfig.BUSINESS_BASE_URL))
                                .addConverterFactory(GsonConverterFactory.create())
                                .build()
                                .create(ApiService.class)
                                .refresh(new AuthDtos.RefreshRequest(session.getRefreshToken()))
                                .execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()
                        && resp.body().data != null) {
                    session.updateToken(resp.body().data.token);
                    return resp.body().data.token;
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }
}
