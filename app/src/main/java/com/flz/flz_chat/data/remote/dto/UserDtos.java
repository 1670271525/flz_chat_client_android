package com.flz.flz_chat.data.remote.dto;

public final class UserDtos {

    private UserDtos() {}

    public static class UserMe {
        public long userId;
        public String userName;
        public String email;
        public String phone;
        public Information information;
    }

    public static class Information {
        public String nickname;
        public String avatarUrl;
        public String mood;
        public String signature;
        public Integer gender;
        public String birthday;
        public String region;
    }

    public static class UpdateMeRequest {
        public String avatarUrl;
        public String mood;
        public String signature;
        public Integer gender;
        public String birthday;
        public String region;
        public String nickname;

        public UpdateMeRequest(String avatarUrl, String mood, String signature, Integer gender,
                               String birthday, String region, String nickname) {
            this.avatarUrl = avatarUrl;
            this.mood = mood;
            this.signature = signature;
            this.gender = gender;
            this.birthday = birthday;
            this.region = region;
            this.nickname = nickname;
        }
    }

    /** 公开资料：兼容 information 嵌套与顶层平铺字段 */
    public static class UserBrief {
        public long userId;
        public String userName;
        public Information information;
        public String nickname;
        public String avatarUrl;
        public String mood;
        public String signature;
        public Integer gender;
        public String birthday;
        public String region;

        public Information resolvedInformation() {
            if (information != null) {
                return information;
            }
            Information info = new Information();
            info.nickname = nickname;
            info.avatarUrl = avatarUrl;
            info.mood = mood;
            info.signature = signature;
            info.gender = gender;
            info.birthday = birthday;
            info.region = region;
            return info;
        }
    }
}
