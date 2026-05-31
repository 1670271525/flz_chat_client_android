package com.flz.flz_chat.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.FriendEntity;
import com.flz.flz_chat.data.remote.dto.ChatDtos;
import com.flz.flz_chat.data.repository.ChatRepository;
import com.flz.flz_chat.data.repository.FriendRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.ui.RealtimeRefreshable;
import com.flz.flz_chat.ui.chat.ChatActivity;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * 好友 Tab：好友列表 + 好友申请。
 */
public class FriendFragment extends Fragment implements RealtimeRefreshable {

    private final FriendRepository friendRepo = new FriendRepository();
    private final ChatRepository chatRepo = new ChatRepository();
    private FriendAdapter friendAdapter;
    private FriendRequestAdapter requestAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getActivity() != null && !AuthGuard.isLoggedIn(getActivity())) {
            return;
        }

        MaterialButton btnSearch = view.findViewById(R.id.btnSearch);
        RecyclerView recyclerFriends = view.findViewById(R.id.recyclerFriends);
        RecyclerView recyclerRequests = view.findViewById(R.id.recyclerRequests);

        friendAdapter = new FriendAdapter(new FriendAdapter.Listener() {
            @Override
            public void onChat(FriendEntity friend) {
                chatRepo.createSingleChat(friend.userId, new ApiCallback<Long>() {
                    @Override
                    public void onSuccess(Long conversationId) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            Intent i = new Intent(getActivity(), ChatActivity.class);
                            i.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId);
                            i.putExtra(ChatActivity.EXTRA_TITLE,
                                    friend.alias != null ? friend.alias : friend.nickname);
                            i.putExtra(ChatActivity.EXTRA_PEER_AVATAR, friend.avatarUrl);
                            startActivity(i);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        showToast(message);
                    }
                });
            }

            @Override
            public void onDetail(FriendEntity friend) {
                Intent i = new Intent(getActivity(), FriendDetailActivity.class);
                i.putExtra(FriendDetailActivity.EXTRA_USER_ID, friend.userId);
                i.putExtra(FriendDetailActivity.EXTRA_NICKNAME, friend.nickname);
                i.putExtra(FriendDetailActivity.EXTRA_ALIAS, friend.alias);
                i.putExtra(FriendDetailActivity.EXTRA_AVATAR, friend.avatarUrl);
                i.putExtra(FriendDetailActivity.EXTRA_SIGNATURE, friend.signature);
                startActivity(i);
            }
        });

        requestAdapter = new FriendRequestAdapter(new FriendRequestAdapter.Listener() {
            @Override
            public void onAccept(ChatDtos.FriendRequestItem item, Runnable onComplete) {
                long requestId = item.resolvedRequestId();
                friendRepo.accept(requestId, new ApiCallback<Long>() {
                    @Override
                    public void onSuccess(Long data) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "已同意好友申请", Toast.LENGTH_SHORT).show();
                            if (data != null && data > 0) {
                                Intent i = new Intent(getActivity(), ChatActivity.class);
                                i.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, data);
                                String title = item.fromNickname != null && !item.fromNickname.trim().isEmpty()
                                        ? item.fromNickname : String.valueOf(item.fromUserId);
                                i.putExtra(ChatActivity.EXTRA_TITLE, title);
                                startActivity(i);
                            }
                            loadData();
                            onComplete.run();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        showToast(message);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(onComplete);
                        }
                    }
                });
            }

            @Override
            public void onReject(ChatDtos.FriendRequestItem item, Runnable onComplete) {
                friendRepo.reject(item.resolvedRequestId(), new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "已拒绝好友申请", Toast.LENGTH_SHORT).show();
                            loadData();
                            onComplete.run();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        showToast(message);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(onComplete);
                        }
                    }
                });
            }
        });

        recyclerFriends.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerFriends.setAdapter(friendAdapter);
        recyclerRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerRequests.setAdapter(requestAdapter);

        btnSearch.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), SearchUserActivity.class)));

        loadData();
    }

    private void loadData() {
        friendRepo.syncFriends(() -> Executors.newSingleThreadExecutor().execute(() -> {
            List<FriendEntity> friends = friendRepo.getLocalFriends();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> friendAdapter.setData(friends));
            }
        }));
        friendRepo.loadIncoming(new ApiCallback<List<ChatDtos.FriendRequestItem>>() {
            @Override
            public void onSuccess(List<ChatDtos.FriendRequestItem> data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> requestAdapter.setData(data));
                }
            }

            @Override
            public void onError(String message) {
                showToast(message);
            }
        });
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    public void onRealtimeRefresh(String eventType) {
        if (eventType != null && (eventType.startsWith("friend.") || eventType.startsWith("conversation."))) {
            loadData();
        }
    }
}
