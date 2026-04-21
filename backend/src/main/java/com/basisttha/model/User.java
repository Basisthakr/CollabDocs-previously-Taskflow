package com.basisttha.model;

import com.basisttha.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    @CreationTimestamp
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "user")
    private List<UserDoc> accessDoc;

    @Column(name = "email_verified")
    private boolean isEmailVerified;

    @Enumerated(EnumType.STRING)
    private Role role;

    // -------------------------------------------------------------------------
    // UserDetails — every method explicitly implemented so there is no
    // ambiguity about which behaviour is active at runtime.
    // -------------------------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Returns the hashed password stored in the database.
     * Lombok's @Getter would generate this, but we override explicitly
     * so the @Override annotation makes the contract visible.
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Spring Security uses getUsername() as the login identifier.
     * We use email as the identifier (not the display-name username field).
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Returns the human-readable display name (the actual username column).
     * Distinct from getUsername() which returns the email used for auth.
     */
    public String getDisplayName() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Account is considered enabled once the email has been verified via OTP.
     * Unverified accounts can still authenticate (2FA sends an OTP instead
     * of issuing tokens directly), so this intentionally returns true always
     * and verification state is checked separately in AuthenticationService.
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", createdAt=" + createdAt +
                ", isEmailVerified=" + isEmailVerified +
                ", role=" + role +
                '}';
    }
}

























