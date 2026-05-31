package com.flz.flz_chat.ui.auth;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.R;
import com.flz.flz_chat.data.remote.dto.AuthDtos;
import com.flz.flz_chat.data.repository.AuthRepository;
import com.flz.flz_chat.ui.main.MainActivity;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import android.content.Intent;

/**
 * 注册页：邮箱验证码 + POST /api/auth/register。
 */
public class RegisterActivity extends AppCompatActivity {

    private final AuthRepository authRepo = new AuthRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextInputEditText etUserName = findViewById(R.id.etUserName);
        TextInputEditText etEmail = findViewById(R.id.etEmail);
        TextInputEditText etCode = findViewById(R.id.etCode);
        TextInputEditText etPassword = findViewById(R.id.etPassword);
        MaterialButton btnSendCode = findViewById(R.id.btnSendCode);
        MaterialButton btnRegister = findViewById(R.id.btnRegister);

        btnSendCode.setOnClickListener(v -> {
            String email = text(etEmail);
            if (email.isEmpty()) {
                Toast.makeText(this, "请填写邮箱", Toast.LENGTH_SHORT).show();
                return;
            }
            authRepo.sendEmailCode(email, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    runOnUiThread(() -> Toast.makeText(RegisterActivity.this,
                            "验证码已发送", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(RegisterActivity.this,
                            message, Toast.LENGTH_SHORT).show());
                }
            });
        });

        btnRegister.setOnClickListener(v -> {
            AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest(
                    text(etUserName),
                    text(etEmail),
                    "",
                    text(etPassword),
                    text(etCode)
            );
            authRepo.register(req, new ApiCallback<AuthDtos.TokenResponse>() {
                @Override
                public void onSuccess(AuthDtos.TokenResponse data) {
                    authRepo.persistToken(data, req.userName);
                    FlzChatApp.get().getWsChatManager().connectIfLoggedIn();
                    runOnUiThread(() -> {
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finish();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(RegisterActivity.this,
                            message, Toast.LENGTH_SHORT).show());
                }
            });
        });
    }

    private String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
