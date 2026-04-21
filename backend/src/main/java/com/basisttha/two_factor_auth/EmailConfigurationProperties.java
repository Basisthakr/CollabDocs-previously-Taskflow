package com.basisttha.two_factor_auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.mail")
public class EmailConfigurationProperties {
    @SuppressWarnings("unused")
    private String username;
}
