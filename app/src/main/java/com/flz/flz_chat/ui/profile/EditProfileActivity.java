package com.flz.flz_chat.ui.profile;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.flz.flz_chat.FlzChatApp;
import com.flz.flz_chat.R;
import com.flz.flz_chat.data.remote.dto.UserDtos;
import com.flz.flz_chat.data.repository.FileRepository;
import com.flz.flz_chat.data.repository.UserRepository;
import com.flz.flz_chat.ui.AuthGuard;
import com.flz.flz_chat.util.ApiCallback;
import com.flz.flz_chat.util.ImageLoader;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.Locale;

/**
 * 编辑资料 PUT /api/users/me。
 */
public class EditProfileActivity extends AppCompatActivity {

    private final UserRepository userRepo = new UserRepository();
    private final FileRepository fileRepo = new FileRepository();
    private boolean loading;
    private boolean signatureEditedByUser;
    private boolean fillingProfile;
    private UserDtos.UserMe currentMe;
    private String avatarObjectKey;
    private boolean avatarChanged;
    private ImageView ivAvatar;
    private ActivityResultLauncher<String> pickAvatarLauncher;
    private AutoCompleteTextView actvMood;
    private AutoCompleteTextView actvGender;
    private TextInputEditText etBirthday;
    private final String[] genderOptions = new String[]{"未知", "男", "女"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireLogin(this)) return;
        setContentView(R.layout.activity_edit_profile);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("编辑资料");
        toolbar.setNavigationOnClickListener(v -> finish());

        ivAvatar = findViewById(R.id.ivAvatar);
        TextInputEditText etNickname = findViewById(R.id.etNickname);
        TextInputEditText etSignature = findViewById(R.id.etSignature);
        actvMood = findViewById(R.id.actvMood);
        TextInputEditText etRegion = findViewById(R.id.etRegion);
        etBirthday = findViewById(R.id.etBirthday);
        actvGender = findViewById(R.id.actvGender);
        MaterialButton btnSave = findViewById(R.id.btnSave);
        MaterialButton btnPickAvatar = findViewById(R.id.btnPickAvatar);

        setupDropdowns();

        pickAvatarLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onAvatarPicked);

        btnPickAvatar.setOnClickListener(v -> {
            if (!loading) pickAvatarLauncher.launch("image/*");
        });

        etBirthday.setOnClickListener(v -> showBirthdayPicker());
        findViewById(R.id.tilBirthday).setOnClickListener(v -> showBirthdayPicker());

        etSignature.addTextChangedListener(simpleWatcher(() -> signatureEditedByUser = true));
        fetchProfile(etNickname, etSignature, etRegion);

        btnSave.setOnClickListener(v -> saveProfile(etNickname, etSignature, etRegion, btnSave));
    }

    private void setupDropdowns() {
        String[] moods = getResources().getStringArray(R.array.mood_options);
        actvMood.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, moods));
        actvGender.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genderOptions));
    }

    private void showBirthdayPicker() {
        Calendar cal = Calendar.getInstance();
        String current = text(etBirthday);
        if (!current.isEmpty()) {
            try {
                String[] parts = current.split("-");
                if (parts.length == 3) {
                    cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                }
            } catch (Exception ignored) {
            }
        }
        new DatePickerDialog(this, (view, year, month, dayOfMonth) ->
                etBirthday.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private TextWatcher simpleWatcher(Runnable afterChange) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!fillingProfile) afterChange.run();
            }
        };
    }

    private void onAvatarPicked(Uri uri) {
        if (uri == null) return;
        setSavingState(findViewById(R.id.btnSave), true);
        String filename = "avatar_" + System.currentTimeMillis() + ".jpg";
        fileRepo.uploadPublicImage(uri, filename, new FileRepository.UploadCallback() {
            @Override
            public void onSuccess(String objectKey) {
                avatarObjectKey = objectKey;
                avatarChanged = true;
                fileRepo.resolveDownloadUrl(objectKey, new ApiCallback<String>() {
                    @Override
                    public void onSuccess(String data) {
                        runOnUiThread(() -> {
                            ImageLoader.load(ivAvatar, data);
                            setSavingState(findViewById(R.id.btnSave), false);
                            Toast.makeText(EditProfileActivity.this, "头像已上传", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> setSavingState(findViewById(R.id.btnSave), false));
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setSavingState(findViewById(R.id.btnSave), false);
                    Toast.makeText(EditProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void saveProfile(TextInputEditText etNickname, TextInputEditText etSignature,
                             TextInputEditText etRegion, MaterialButton btnSave) {
        if (loading) return;
        String nickname = text(etNickname);
        if (nickname.isEmpty()) {
            Toast.makeText(this, "昵称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        String signatureInput = text(etSignature);
        String signatureValue;
        if (signatureEditedByUser) {
            signatureValue = signatureInput.isEmpty() ? null : signatureInput;
        } else if (currentMe != null && currentMe.information != null) {
            signatureValue = currentMe.information.signature;
        } else {
            signatureValue = signatureInput.isEmpty() ? null : signatureInput;
        }

        Integer gender = parseGenderSelection();
        String mood = textOrNull(actvMood);
        String region = textOrNull(etRegion);
        String birthday = textOrNull(etBirthday);
        String avatarUrl = avatarChanged ? avatarObjectKey : null;

        setSavingState(btnSave, true);
        userRepo.updateMe(new UserDtos.UpdateMeRequest(
                        avatarUrl, mood, signatureValue, gender, birthday, region, nickname),
                new ApiCallback<UserDtos.UserMe>() {
                    @Override
                    public void onSuccess(UserDtos.UserMe data) {
                        if (data != null && data.information != null && data.information.nickname != null) {
                            FlzChatApp.get().getSessionManager().saveProfile(data.information.nickname);
                        } else {
                            FlzChatApp.get().getSessionManager().saveProfile(nickname);
                        }
                        runOnUiThread(() -> {
                            setSavingState(btnSave, false);
                            Toast.makeText(EditProfileActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(EditProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                            setSavingState(btnSave, false);
                        });
                    }
                });
    }

    private String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private String textOrNull(TextInputEditText et) {
        String v = text(et);
        return v.isEmpty() ? null : v;
    }

    private String textOrNull(AutoCompleteTextView et) {
        String v = et.getText() != null ? et.getText().toString().trim() : "";
        return v.isEmpty() ? null : v;
    }

    private Integer parseGenderSelection() {
        String label = actvGender.getText() != null ? actvGender.getText().toString().trim() : "";
        if (label.isEmpty()) {
            return null;
        }
        for (int i = 0; i < genderOptions.length; i++) {
            if (genderOptions[i].equals(label)) {
                return i;
            }
        }
        return null;
    }

    private void fetchProfile(TextInputEditText etNickname, TextInputEditText etSignature,
                              TextInputEditText etRegion) {
        userRepo.fetchMe(new ApiCallback<UserDtos.UserMe>() {
            @Override
            public void onSuccess(UserDtos.UserMe data) {
                currentMe = data;
                if (data == null || data.information == null) return;
                runOnUiThread(() -> {
                    fillingProfile = true;
                    UserDtos.Information info = data.information;
                    if (info.nickname != null) etNickname.setText(info.nickname);
                    if (info.signature != null) etSignature.setText(info.signature);
                    if (info.mood != null) actvMood.setText(info.mood, false);
                    if (info.region != null) etRegion.setText(info.region);
                    if (info.birthday != null) etBirthday.setText(info.birthday);
                    if (info.gender != null && info.gender >= 0 && info.gender < genderOptions.length) {
                        actvGender.setText(genderOptions[info.gender], false);
                    }
                    avatarObjectKey = info.avatarUrl;
                    loadAvatarPreview(info.avatarUrl);
                    fillingProfile = false;
                    signatureEditedByUser = false;
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(EditProfileActivity.this,
                        message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadAvatarPreview(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) return;
        if (avatarUrl.startsWith("http")) {
            ImageLoader.load(ivAvatar, avatarUrl);
            return;
        }
        fileRepo.resolveDownloadUrl(avatarUrl, new ApiCallback<String>() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> ImageLoader.load(ivAvatar, data));
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void setSavingState(MaterialButton btnSave, boolean saving) {
        loading = saving;
        btnSave.setEnabled(!saving);
        btnSave.setText(saving ? "保存中..." : "保存");
    }
}
