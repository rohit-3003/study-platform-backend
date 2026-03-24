package com.studyplatform.controller;

import com.studyplatform.dto.ApiResponse;
import com.studyplatform.dto.AuthDto;
import com.studyplatform.entity.ExamType;
import com.studyplatform.entity.User;
import com.studyplatform.repository.UserRepository;
import com.studyplatform.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final AuthService authService;
    private final UserRepository userRepo;

    public ProfileController(AuthService authService, UserRepository userRepo) {
        this.authService = authService;
        this.userRepo = userRepo;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AuthDto.UserInfo>> getProfile() {
        User user = authService.getCurrentUser();
        var info = AuthDto.UserInfo.builder()
                .id(user.getId()).fullName(user.getFullName())
                .email(user.getEmail()).role(user.getRole().name())
                .examType(user.getExamType() != null ? user.getExamType().name() : null)
                .isVerified(user.isVerified()).build();
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AuthDto.UserInfo>> updateProfile(@RequestBody Map<String, String> updates) {
        User user = authService.getCurrentUser();

        if (updates.containsKey("fullName") && !updates.get("fullName").isBlank()) {
            user.setFullName(updates.get("fullName"));
        }
        if (updates.containsKey("phone")) {
            user.setPhone(updates.get("phone"));
        }
        if (updates.containsKey("examType") && !updates.get("examType").isBlank()) {
            try {
                user.setExamType(ExamType.valueOf(updates.get("examType")));
            } catch (IllegalArgumentException ignored) {}
        }

        user = userRepo.save(user);

        var info = AuthDto.UserInfo.builder()
                .id(user.getId()).fullName(user.getFullName())
                .email(user.getEmail()).role(user.getRole().name())
                .examType(user.getExamType() != null ? user.getExamType().name() : null)
                .isVerified(user.isVerified()).build();
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", info));
    }
}
