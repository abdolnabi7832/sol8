package com.institutional.forex.controller;

import com.institutional.forex.engine.sync.FileDatabaseSyncService;
import com.institutional.forex.model.entity.UserSettingsEntity;
import com.institutional.forex.repository.UserSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserSettingsRepository userSettingsRepository;
    private final FileDatabaseSyncService syncService;

    public AuthController(UserSettingsRepository userSettingsRepository,
                          FileDatabaseSyncService syncService) {
        this.userSettingsRepository = userSettingsRepository;
        this.syncService = syncService;
    }

    public record LoginRequest(String username, String password) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
    public record UpdateCredentialsRequest(String currentPassword, String newUsername, String newPassword) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        UserSettingsEntity settings = userSettingsRepository.findByUserIdentifier("DEFAULT_USER")
                .orElseGet(() -> {
                    UserSettingsEntity s = new UserSettingsEntity();
                    return userSettingsRepository.save(s);
                });

        String validUser = settings.getUsername() != null && !settings.getUsername().isBlank() ? settings.getUsername() : "admin";
        String validPass = settings.getPasswordHash() != null && !settings.getPasswordHash().isBlank() ? settings.getPasswordHash() : "admin";

        if (req.username() != null && req.username().trim().equalsIgnoreCase(validUser.trim()) &&
            req.password() != null && req.password().trim().equals(validPass.trim())) {
            
            String token = UUID.randomUUID().toString();
            settings.setAuthToken(token);
            userSettingsRepository.save(settings);
            syncService.syncAllFiles();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "token", token,
                    "username", validUser,
                    "message", "Institutional Login Authorized"
            ));
        }

        return ResponseEntity.status(401).body(Map.of(
                "status", "UNAUTHORIZED",
                "message", "نام کاربری یا رمز عبور نامعتبر است (پیش‌فرض: admin / admin)"
        ));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }
        String token = authHeader.substring(7).trim();
        UserSettingsEntity settings = userSettingsRepository.findByUserIdentifier("DEFAULT_USER")
                .orElse(null);

        if (settings != null && token.equals(settings.getAuthToken())) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "username", settings.getUsername() != null ? settings.getUsername() : "admin"
            ));
        }
        return ResponseEntity.status(401).body(Map.of("authenticated", false));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        UserSettingsEntity settings = userSettingsRepository.findByUserIdentifier("DEFAULT_USER").orElse(null);
        if (settings != null) {
            settings.setAuthToken(null);
            userSettingsRepository.save(settings);
            syncService.syncAllFiles();
        }
        return ResponseEntity.ok(Map.of("status", "LOGGED_OUT"));
    }

    @PostMapping("/update-credentials")
    public ResponseEntity<?> updateCredentials(@RequestBody UpdateCredentialsRequest req) {
        UserSettingsEntity settings = userSettingsRepository.findByUserIdentifier("DEFAULT_USER").orElse(null);
        if (settings == null) {
            return ResponseEntity.status(404).body(Map.of("error", "تنظیمات کاربر یافت نشد"));
        }

        String currentPass = settings.getPasswordHash() != null && !settings.getPasswordHash().isBlank() ? settings.getPasswordHash() : "admin";
        if (req.currentPassword() == null || !currentPass.equals(req.currentPassword().trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "رمز عبور فعلی مطابقت ندارد."));
        }

        if (req.newUsername() != null && !req.newUsername().trim().isBlank()) {
            settings.setUsername(req.newUsername().trim());
        }

        if (req.newPassword() != null && !req.newPassword().trim().isBlank()) {
            if (req.newPassword().trim().length() < 3) {
                return ResponseEntity.badRequest().body(Map.of("error", "رمز عبور جدید باید حداقل ۳ کاراکتر باشد."));
            }
            settings.setPasswordHash(req.newPassword().trim());
        }

        userSettingsRepository.save(settings);
        syncService.syncAllFiles();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "اطلاعات ورود (نام کاربری / رمز عبور) با موفقیت به‌روزرسانی شد.",
                "username", settings.getUsername()
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req) {
        return updateCredentials(new UpdateCredentialsRequest(req.currentPassword(), null, req.newPassword()));
    }
}
