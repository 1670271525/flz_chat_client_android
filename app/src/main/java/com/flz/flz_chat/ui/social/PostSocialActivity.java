package com.flz.flz_chat.ui.social;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.flz.flz_chat.R;
import com.flz.flz_chat.data.repository.SocialRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/**
 * 发布动态 POST /api/social。
 */
public class PostSocialActivity extends AppCompatActivity {

    private final SocialRepository repo = new SocialRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireLogin(this)) return;
        setContentView(R.layout.activity_post_social);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        EditText etContent = findViewById(R.id.etContent);
        MaterialButton btnPublish = findViewById(R.id.btnPublish);

        btnPublish.setOnClickListener(v -> {
            String content = etContent.getText() != null ? etContent.getText().toString().trim() : "";
            if (content.isEmpty()) {
                Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
                return;
            }
            repo.post(content, 0, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    runOnUiThread(() -> {
                        Toast.makeText(PostSocialActivity.this, "发布成功", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(PostSocialActivity.this,
                            message, Toast.LENGTH_SHORT).show());
                }
            });
        });
    }
}
