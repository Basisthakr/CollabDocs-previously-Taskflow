package com.basisttha.security;

import com.basisttha.exception.OtpException;
import com.basisttha.exception.TokenInvalidException;
import com.basisttha.exception.UserAlreadyExistException;
import com.basisttha.exception.UserException;
import com.basisttha.request.auth.AuthenticationRequest;
import com.basisttha.request.auth.OtpVerificationRequest;
import com.basisttha.request.auth.RegisterRequest;
import com.basisttha.request.auth.ResetPasswordRequest;
import com.basisttha.response.AuthenticationResponse;
import com.basisttha.security.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request) throws UserAlreadyExistException {
        return ResponseEntity.ok(authenticationService.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(
            @Valid @RequestBody AuthenticationRequest request) throws UserException {
        return authenticationService.authenticate(request);
    }

    @PutMapping("/enable-double-auth")
    public ResponseEntity<?> enableDoubleAuth(
            @Valid @RequestBody AuthenticationRequest request) throws UserException {
        return authenticationService.enableTwoFactAuth(request);
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<AuthenticationResponse> refreshToken(
            @RequestHeader("Authorization") String refreshToken) throws TokenInvalidException {
        return ResponseEntity.ok(authenticationService.refreshToken(refreshToken));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> otpVerificationHandler(
            @RequestBody OtpVerificationRequest request) throws ExecutionException, OtpException {
        return authenticationService.verifyOtp(request);
    }

    @PutMapping("/reset-password")
    public ResponseEntity<?> resetPasswordHandler(
            @RequestBody ResetPasswordRequest request) throws UserException {
        return authenticationService.resetPassword(request);
    }
}
