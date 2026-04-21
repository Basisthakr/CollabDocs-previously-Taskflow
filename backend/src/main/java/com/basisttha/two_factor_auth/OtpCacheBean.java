package com.basisttha.two_factor_auth;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(OneTimePasswordConfigurationProperties.class)
public class OtpCacheBean {

    private final OneTimePasswordConfigurationProperties otpConfig;

    /**
     * In-memory OTP cache keyed by email address.
     *
     * <p>Values are Maps containing the OTP and user data set at generation
     * time.  The cache expires entries after {@code expirationMinutes} so that
     * unverified OTPs cannot be replayed indefinitely.
     *
     * <p>Note: this is a process-local cache.  In a horizontally-scaled
     * deployment, move this to Redis so that an OTP generated on node A can
     * be verified on node B.
     */
    @Bean
    public LoadingCache<String, Object> loadingCache() {
        int expirationMinutes = otpConfig.getOtp().getExpirationMinutes();
        return CacheBuilder.newBuilder()
                .expireAfterWrite(expirationMinutes, TimeUnit.MINUTES)
                .build(new CacheLoader<String, Object>() {
                    @Override
                    public Object load(String key) {
                        // Return a sentinel — the actual value is always set via put().
                        return new Object();
                    }
                });
    }
}
