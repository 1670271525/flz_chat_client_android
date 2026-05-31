package com.flz.flz_chat.ui.social;

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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.R;
import com.flz.flz_chat.data.local.entity.SocialEntity;
import com.flz.flz_chat.data.repository.SocialRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.ui.RealtimeRefreshable;
import com.flz.flz_chat.ui.main.MainActivity;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * 社交 Tab：好友动态流 GET /api/social/feed，缓存到 SQLite。
 */
public class SocialFeedFragment extends Fragment implements RealtimeRefreshable {

    private final SocialRepository repo = new SocialRepository();
    private SocialAdapter adapter;
    private SwipeRefreshLayout swipe;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_social_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getActivity() != null && !AuthGuard.isLoggedIn(getActivity())) return;

        swipe = view.findViewById(R.id.swipeRefresh);
        RecyclerView recycler = view.findViewById(R.id.recycler);
        FloatingActionButton fab = view.findViewById(R.id.fabPost);

        long myUserId = FlzChatApp.get().getSessionManager().getUserId();
        adapter = new SocialAdapter(myUserId, (post, position) ->
                repo.toggleLike(post, new ApiCallback<SocialEntity>() {
                    @Override
                    public void onSuccess(SocialEntity data) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> adapter.updatePost(position, data));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                                refresh();
                            });
                        }
                    }
                }));
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(this::refresh);
        fab.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), PostSocialActivity.class)));

        refresh();
    }

    private void refresh() {
        swipe.setRefreshing(true);
        repo.syncFeed(() -> Executors.newSingleThreadExecutor().execute(() -> {
            List<SocialEntity> list = repo.getLocalFeed();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    swipe.setRefreshing(false);
                    adapter.setData(list);
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshNavBadges();
                    }
                });
            }
        }));
    }

    @Override
    public void onRealtimeRefresh(String eventType) {
        if (swipe != null && eventType != null && eventType.startsWith("social.")) {
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        repo.markFeedSeen(() -> {
            if (getActivity() instanceof MainActivity) {
                getActivity().runOnUiThread(((MainActivity) getActivity())::refreshNavBadges);
            }
        });
        if (swipe != null) refresh();
    }
}
