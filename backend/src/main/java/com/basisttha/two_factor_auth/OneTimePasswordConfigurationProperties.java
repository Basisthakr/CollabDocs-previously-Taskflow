package com.basisttha.two_factor_auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "two.factor.auth")
public class OneTimePasswordConfigurationProperties {
    @SuppressWarnings("unused")
    private OTP otp = new OTP();

    @Data
    public class OTP {
        @SuppressWarnings("unused")
        private Integer expirationMinutes;
    }
}
