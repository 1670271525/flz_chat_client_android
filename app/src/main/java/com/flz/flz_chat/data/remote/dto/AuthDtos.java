package com.flz.flz_chat.data.remote.dto;

public final class AuthDtos {

    private AuthDtos() {}

    public static class LoginRequest {
        public String account;
        public String password;

        public LoginRequest(String account, String password) {
            this.account = account;
            this.password = password;
        }
    }

    public static class RegisterRequest {
        public String userName;
        public String email;
        public String phone;
        public String password;
        public String emailCode;

        public RegisterRequest(String userName, String email, String phone,
                               String password, String emailCode) {
            this.userName = userName;
            this.email = email;
            this.phone = phone;
            this.password = password;
            this.emailCode = emailCode;
        }
    }

    public static class EmailCodeRequest {
        public String email;
        public String scene;

        public EmailCodeRequest(String email, String scene) {
            this.email = email;
            this.scene = scene;
        }
    }

    public static class RefreshRequest {
        public String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class TokenResponse {
        public long userId;
        public String token;
        public String refreshToken;
        public String expireAt;
    }
}
