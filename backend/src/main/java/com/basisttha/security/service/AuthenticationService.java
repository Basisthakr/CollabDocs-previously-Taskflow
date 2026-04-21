package com.basisttha.security.service;

import com.basisttha.request.auth.*;
import com.basisttha.security.jwt.JwtService;
import com.basisttha.exception.OtpException;
import com.basisttha.exception.TokenInvalidException;
import com.basisttha.exception.UserAlreadyExistException;
import com.basisttha.exception.UserException;
import com.basisttha.model.enums.Role;
import com.basisttha.model.User;
import com.basisttha.repo.UserRepository;
import com.basisttha.response.AuthenticationResponse;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@AllArgsConstructor
public class AuthenticationService {

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoadingCache<String, Object> oneTimePasswordCache;
    private final EmailService         emailService;

    /**
     * Cryptographically secure random for OTP generation.
     * {@link SecureRandom} is thread-safe and seeded from the OS entropy pool,
     * unlike {@link java.util.Random} which is predictable given the seed.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public AuthenticationResponse register(RegisterRequest request) throws UserAlreadyExistException {
        if (userRepository.findByUsernameOrEmail(request.getUsername(), request.getEmail()).isPresent()) {
            throw new UserAlreadyExistException(
                    "Email or username already in use: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .isEmailVerified(false)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: {} ({})", saved.getDisplayName(), saved.getEmail());

        return AuthenticationResponse.builder()
                .accessToken(jwtService.generateToken(saved))
                .refreshToken(jwtService.generateRefreshToken(saved))
                .displayName(saved.getDisplayName())
                .build();
    }

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    public ResponseEntity<?> authenticate(AuthenticationRequest request) throws UserException {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserException(request.getEmail() + " does not exist"));

        // Let Spring Security validate the password (throws on failure).
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        if (user.isEmailVerified()) {
            // 2FA is enabled — send OTP and withhold tokens until verified.
            sendOtp(user, "2FA: Login request");
            log.info("2FA OTP sent to {}", user.getEmail());
            return ResponseEntity.ok(otpSentResponse());
        }

        log.info("User {} authenticated (no 2FA)", user.getDisplayName());
        return ResponseEntity.ok(AuthenticationResponse.builder()
                .accessToken(jwtService.generateToken(user))
                .refreshToken(jwtService.generateRefreshToken(user))
                .displayName(user.getDisplayName())
                .build());
    }

    // -------------------------------------------------------------------------
    // OTP verification
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public ResponseEntity<?> verifyOtp(OtpVerificationRequest dto)
            throws ExecutionException, OtpException {

        Object cached = oneTimePasswordCache.get(dto.getEmailId());

        if (!(cached instanceof Map)) {
            throw new OtpException("No pending OTP for " + dto.getEmailId());
        }

        Map<String, Object> data = (Map<String, Object>) cached;
        int storedOtp = (Integer) data.get("otp");

        if (storedOtp != dto.getOneTimePassword()) {
            log.warn("Invalid OTP attempt for {}", dto.getEmailId());
            throw new OtpException("Invalid OTP");
        }

        oneTimePasswordCache.invalidate(dto.getEmailId());

        return switch (dto.getContext()) {
            case SIGN_UP -> {
                User user = User.builder()
                        .username((String) data.get("username"))
                        .email((String) data.get("email"))
                        .password((String) data.get("password"))
                        .role((Role) data.get("role"))
                        .createdAt(LocalDateTime.now())
                        .isEmailVerified(true)
                        .build();
                user = userRepository.save(user);
                log.info("User {} verified email and completed registration", user.getDisplayName());
                yield ResponseEntity.ok(AuthenticationResponse.builder()
                        .accessToken(jwtService.generateToken(user))
                        .refreshToken(jwtService.generateRefreshToken(user))
                        .displayName(user.getDisplayName())
                        .build());
            }
            case LOGIN -> {
                User loginUser = userRepository.findByEmail(dto.getEmailId())
                        .orElseThrow(() -> new UsernameNotFoundException(dto.getEmailId()));
                log.info("User {} completed 2FA login", loginUser.getDisplayName());
                yield ResponseEntity.ok(AuthenticationResponse.builder()
                        .accessToken(jwtService.generateToken(loginUser))
                        .refreshToken(jwtService.generateRefreshToken(loginUser))
                        .displayName(loginUser.getDisplayName())
                        .build());
            }
            case RESET_PASSWORD -> {
                User user = userRepository.findByEmail(dto.getEmailId())
                        .orElseThrow(() -> new UsernameNotFoundException(dto.getEmailId()));
                user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
                userRepository.save(user);
                log.info("Password reset for {}", user.getDisplayName());
                yield ResponseEntity.ok("Password reset successfully.");
            }
            case ENABLE_TWO_FACT_AUTH -> {
                User user = userRepository.findByEmail(dto.getEmailId())
                        .orElseThrow(() -> new UsernameNotFoundException(dto.getEmailId()));
                user.setEmailVerified(!user.isEmailVerified());
                userRepository.save(user);
                log.info("2FA toggled for {} — now {}",
                        user.getDisplayName(), user.isEmailVerified() ? "enabled" : "disabled");
                yield ResponseEntity.ok("Two-factor authentication updated successfully.");
            }
            default -> throw new OtpException("Unsupported OTP context: " + dto.getContext());
        };
    }

    // -------------------------------------------------------------------------
    // Token refresh
    // -------------------------------------------------------------------------

    public AuthenticationResponse refreshToken(String authHeader) throws TokenInvalidException {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new TokenInvalidException("Authorization header is missing or malformed");
        }

        String token     = authHeader.substring(7);
        String userEmail = jwtService.extractUsername(token);

        if (userEmail == null) {
            throw new TokenInvalidException("Could not extract user from refresh token");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException(userEmail));

        if (!jwtService.validateToken(token, user)) {
            throw new TokenInvalidException("Refresh token is expired or invalid");
        }

        return new AuthenticationResponse(
                jwtService.generateToken(user), token, user.getDisplayName());
    }

    // -------------------------------------------------------------------------
    // Password reset / 2FA toggle
    // -------------------------------------------------------------------------

    public ResponseEntity<?> resetPassword(ResetPasswordRequest request) throws UserException {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserException("No account with email: " + request.email()));
        sendOtp(user, "Password Reset");
        return ResponseEntity.ok(otpSentResponse());
    }

    public ResponseEntity<?> enableTwoFactAuth(AuthenticationRequest request) throws UserException {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserException(request.getEmail() + " does not exist"));
        sendOtp(user, "Enable Two-Factor Authentication");
        return ResponseEntity.ok(otpSentResponse());
    }

    // -------------------------------------------------------------------------
    // OTP helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a 6-digit OTP using {@link SecureRandom}, stores user data in
     * the Guava cache keyed by email, and dispatches the email asynchronously.
     *
     * <p>Using {@code SecureRandom} instead of {@code Random} ensures the OTP
     * cannot be predicted from the JVM startup time or any observable seed.
     */
    private void sendOtp(User user, String subject) {
        // 6-digit OTP: 100000 – 999999
        int otp = 100_000 + SECURE_RANDOM.nextInt(900_000);

        Map<String, Object> data = new HashMap<>();
        data.put("otp",      otp);
        data.put("username", user.getUsername());
        data.put("email",    user.getEmail());
        data.put("role",     user.getRole());
        data.put("password", user.getPassword());

        oneTimePasswordCache.put(user.getEmail(), data);

        log.debug("OTP generated for {} (log only in dev — remove in prod)", user.getEmail());

        CompletableFuture.runAsync(() ->
                emailService.sendEmail(user.getEmail(), subject, "Your OTP is: " + otp)
        ).exceptionally(ex -> {
            log.error("Failed to send OTP email to {}: {}", user.getEmail(), ex.getMessage());
            return null;
        });
    }

    private Map<String, String> otpSentResponse() {
        return Map.of("message",
                "OTP sent to your registered email. Verify via POST /auth/verify-otp");
    }
}
