package com.flz.flz_chat.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.R;
import com.flz.flz_chat.data.remote.dto.AuthDtos;
import com.flz.flz_chat.data.repository.AuthRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.ui.main.MainActivity;
import com.flz.flz_chat.util.ApiCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 登录页：调用业务 POST /api/auth/login，成功后建立 WS 并进入主页。
 */
public class LoginActivity extends AppCompatActivity {

    private final AuthRepository authRepo = new AuthRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AuthGuard.isLoggedIn(this)) {
            goMain();
            return;
        }
        setContentView(R.layout.activity_login);

        TextInputEditText etAccount = findViewById(R.id.etAccount);
        TextInputEditText etPassword = findViewById(R.id.etPassword);
        MaterialButton btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            String account = etAccount.getText() != null ? etAccount.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
            if (account.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入账号和密码", Toast.LENGTH_SHORT).show();
                return;
            }
            btnLogin.setEnabled(false);
            authRepo.login(account, password, new ApiCallback<AuthDtos.TokenResponse>() {
                @Override
                public void onSuccess(AuthDtos.TokenResponse data) {
                    authRepo.persistToken(data, account);
                    FlzChatApp.get().getWsChatManager().connectIfLoggedIn();
                    runOnUiThread(() -> {
                        btnLogin.setEnabled(true);
                        goMain();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        btnLogin.setEnabled(true);
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        findViewById(R.id.tvGoRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
