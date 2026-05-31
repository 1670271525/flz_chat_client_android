package com.flz.flz_chat.data.remote;

import com.flz.flz_chat.data.remote.dto.ApiResult;
import com.flz.flz_chat.data.remote.dto.AuthDtos;
import com.flz.flz_chat.data.remote.dto.ChatDtos;
import com.flz.flz_chat.data.remote.dto.FileDtos;
import com.flz.flz_chat.data.remote.dto.PageResult;
import com.flz.flz_chat.data.remote.dto.UserDtos;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * flz_chat_business HTTP 接口定义，与 CLIENT_DEVELOPMENT_GUIDE 对齐。
 */
public interface ApiService {

    @POST("api/auth/login")
    Call<ApiResult<AuthDtos.TokenResponse>> login(@Body AuthDtos.LoginRequest body);

    @POST("api/auth/register")
    Call<ApiResult<AuthDtos.TokenResponse>> register(@Body AuthDtos.RegisterRequest body);

    @POST("api/auth/email-code")
    Call<ApiResult<Void>> sendEmailCode(@Body AuthDtos.EmailCodeRequest body);

    @POST("api/auth/refresh")
    Call<ApiResult<AuthDtos.TokenResponse>> refresh(@Body AuthDtos.RefreshRequest body);

    @POST("api/auth/logout")
    Call<ApiResult<Void>> logout();

    @GET("api/users/me")
    Call<ApiResult<UserDtos.UserMe>> getMe();

    @PUT("api/users/me")
    Call<ApiResult<UserDtos.UserMe>> updateMe(@Body UserDtos.UpdateMeRequest body);

    @GET("api/users/{userId}")
    Call<ApiResult<UserDtos.UserBrief>> getUser(@Path("userId") long userId);

    @GET("api/users/search")
    Call<ApiResult<PageResult<UserDtos.UserBrief>>> searchUsers(
            @Query("keyword") String keyword,
            @Query("page") int page,
            @Query("size") int size);

    @GET("api/conversations")
    Call<ApiResult<PageResult<ChatDtos.ConversationItem>>> getConversations(
            @Query("page") int page,
            @Query("size") int size);

    @POST("api/conversations/single")
    Call<ApiResult<ChatDtos.ConversationIdResponse>> createSingle(
            @Body ChatDtos.SingleChatRequest body);

    @PUT("api/conversations/{id}/read")
    Call<ApiResult<Void>> markRead(@Path("id") long id, @Body ChatDtos.ReadRequest body);

    @GET("api/messages")
    Call<ApiResult<PageResult<ChatDtos.MessageItem>>> getMessages(
            @Query("conversationId") long conversationId,
            @Query("beforeId") Long beforeId,
            @Query("size") int size);

    @POST("api/messages")
    Call<ApiResult<FileDtos.SendMessageResponse>> sendMessage(@Body ChatDtos.SendMessageRequest body);

    @GET("api/friends")
    Call<ApiResult<PageResult<ChatDtos.FriendItem>>> getFriends(
            @Query("page") int page,
            @Query("size") int size);

    @POST("api/friends/requests")
    Call<ApiResult<Void>> sendFriendRequest(@Body ChatDtos.FriendRequestBody body);

    @GET("api/friends/requests/incoming")
    Call<ApiResult<PageResult<ChatDtos.FriendRequestItem>>> getIncomingRequests(
            @Query("status") int status,
            @Query("page") int page,
            @Query("size") int size);

    @POST("api/friends/requests/{id}/accept")
    Call<ApiResult<ChatDtos.ConversationIdResponse>> acceptRequest(@Path("id") long requestId);

    @POST("api/friends/requests/{id}/reject")
    Call<ApiResult<Void>> rejectRequest(@Path("id") long requestId);

    @PUT("api/friends/{friendId}")
    Call<ApiResult<Void>> updateFriendAlias(@Path("friendId") long friendId, @Body ChatDtos.FriendAliasBody body);

    @POST("api/friends/{friendId}/block")
    Call<ApiResult<Void>> blockFriend(@Path("friendId") long friendId);

    @POST("api/friends/{friendId}/unblock")
    Call<ApiResult<Void>> unblockFriend(@Path("friendId") long friendId);

    @DELETE("api/friends/{friendId}")
    Call<ApiResult<Void>> deleteFriend(@Path("friendId") long friendId);

    @POST("api/files/presign")
    Call<ApiResult<FileDtos.PresignUploadResponse>> presignUpload(@Body FileDtos.PresignUploadRequest body);

    @GET("api/files/presign")
    Call<ApiResult<FileDtos.PresignDownloadResponse>> presignDownload(@Query("objectKey") String objectKey);

    @GET("api/social/feed")
    Call<ApiResult<PageResult<ChatDtos.SocialPostItem>>> getSocialFeed(
            @Query("page") int page,
            @Query("size") int size);

    @GET("api/social/users/{userId}")
    Call<ApiResult<PageResult<ChatDtos.SocialPostItem>>> getUserSocial(
            @Path("userId") long userId,
            @Query("page") int page,
            @Query("size") int size);

    @POST("api/social")
    Call<ApiResult<ChatDtos.SocialPostItem>> postSocial(@Body ChatDtos.PostSocialRequest body);

    @POST("api/social/{id}/like")
    Call<ApiResult<Void>> likeSocial(@Path("id") long socialId);

    @DELETE("api/social/{id}/like")
    Call<ApiResult<Void>> unlikeSocial(@Path("id") long socialId);
}
