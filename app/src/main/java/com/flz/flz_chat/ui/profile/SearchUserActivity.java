package com.flz.flz_chat.ui.profile;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.remote.dto.UserDtos;
import com.flz.flz_chat.data.repository.FriendRepository;
import com.flz.flz_chat.data.repository.UserRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 搜索用户并发起好友申请。
 */
public class SearchUserActivity extends AppCompatActivity {

    private final UserRepository userRepo = new UserRepository();
    private final FriendRepository friendRepo = new FriendRepository();
    private SearchUserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireLogin(this)) return;
        setContentView(R.layout.activity_search_user);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        EditText etKeyword = findViewById(R.id.etKeyword);
        MaterialButton btnSearch = findViewById(R.id.btnSearch);
        RecyclerView recycler = findViewById(R.id.recycler);

        adapter = new SearchUserAdapter(user -> friendRepo.sendRequest(user.userId, "你好",
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        runOnUiThread(() -> Toast.makeText(SearchUserActivity.this,
                                "申请已发送", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(SearchUserActivity.this,
                                message, Toast.LENGTH_SHORT).show());
                    }
                }));
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        btnSearch.setOnClickListener(v -> {
            String kw = etKeyword.getText() != null ? etKeyword.getText().toString().trim() : "";
            if (kw.isEmpty()) return;
            userRepo.search(kw, new ApiCallback<com.flz.flz_chat.data.remote.dto.PageResult<UserDtos.UserBrief>>() {
                @Override
                public void onSuccess(com.flz.flz_chat.data.remote.dto.PageResult<UserDtos.UserBrief> data) {
                    List<UserDtos.UserBrief> list = data != null && data.records != null
                            ? data.records : java.util.Collections.emptyList();
                    runOnUiThread(() -> adapter.setData(list));
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(SearchUserActivity.this,
                            message, Toast.LENGTH_SHORT).show());
                }
            });
        });
    }
}
