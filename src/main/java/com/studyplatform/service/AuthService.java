package com.studyplatform.service;

import com.studyplatform.dto.AuthDto;
import com.studyplatform.entity.*;
import com.studyplatform.exception.GlobalExceptionHandler.*;
import com.studyplatform.repository.*;
import com.studyplatform.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuthService — handles registration, login, token refresh, and password reset.
 */
@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       SubscriptionRepository subscriptionRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    /** Register a new user with free subscription */
    @Transactional
    public AuthDto.AuthResponse signup(AuthDto.SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered");
        }

        // Create user
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .examType(request.getExamType())
                .verificationToken(UUID.randomUUID().toString())
                .build();
        userRepository.save(user);

        // Create free subscription
        Subscription freeSub = Subscription.builder()
                .user(user)
                .planType(Subscription.PlanType.FREE)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .startsAt(LocalDateTime.now())
                .build();
        subscriptionRepository.save(freeSub);

        logger.info("New user registered: {}", user.getEmail());

        // Generate tokens
        String accessToken = tokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = createRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken, "FREE");
    }

    /** Login with email and password */
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = createRefreshToken(user);

        // Get subscription plan
        String plan = subscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .map(s -> s.getPlanType().name())
                .orElse("FREE");

        return buildAuthResponse(user, accessToken, refreshToken, plan);
    }

    /** Refresh access token using refresh token */
    @Transactional
    public AuthDto.AuthResponse refreshToken(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException("Refresh token expired. Please login again.");
        }

        User user = refreshToken.getUser();
        String newAccessToken = tokenProvider.generateAccessToken(user.getEmail());

        // Rotate refresh token for security
        refreshTokenRepository.delete(refreshToken);
        String newRefreshToken = createRefreshToken(user);

        String plan = subscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .map(s -> s.getPlanType().name())
                .orElse("FREE");

        return buildAuthResponse(user, newAccessToken, newRefreshToken, plan);
    }

    /** Initiate password reset */
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

        user.setResetToken(UUID.randomUUID().toString());
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        // In production, send email with reset link
        logger.info("Password reset token generated for: {}", email);
    }

    /** Reset password using token */
    @Transactional
    public void resetPassword(AuthDto.ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new BusinessException("Invalid reset token"));

        if (user.getResetTokenExpiry() != null && user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Reset token has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        // Invalidate all refresh tokens for security
        refreshTokenRepository.deleteByUserId(user.getId());
        logger.info("Password reset successful for: {}", user.getEmail());
    }

    /** Get current authenticated user */
    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    // --- Private helpers ---

    private String createRefreshToken(User user) {
        String tokenStr = tokenProvider.generateRefreshToken(user.getEmail());
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenStr)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);
        return tokenStr;
    }

    private AuthDto.AuthResponse buildAuthResponse(User user, String accessToken,
                                                    String refreshToken, String plan) {
        return AuthDto.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenExpiration() / 1000)
                .user(AuthDto.UserInfo.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .examType(user.getExamType() != null ? user.getExamType().name() : null)
                        .isVerified(user.isVerified())
                        .subscriptionPlan(plan)
                        .build())
                .build();
    }
}
