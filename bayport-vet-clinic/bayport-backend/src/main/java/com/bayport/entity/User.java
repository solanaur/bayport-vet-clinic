package com.bayport.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String username;

    @JsonIgnore
    @Column(nullable = true, columnDefinition = "VARCHAR(255) DEFAULT NULL") // Allow null for existing users
    private String password; // Will store hashed password

    @Column(name = "full_name")
    private String fullName;

    private String email;

    @Column(name = "mfa_enabled", nullable = true, columnDefinition = "BOOLEAN DEFAULT FALSE") // Allow null for existing users
    private Boolean mfaEnabled = false;

    /** Encrypted TOTP secret for Google Authenticator. Never returned on list/get user APIs. */
    @JsonIgnore
    @Column(name = "totp_secret", length = 512)
    private String totpSecret;

    /** Last TOTP time step that was accepted. Older or equal codes are rejected. */
    @JsonIgnore
    @Column(name = "totp_last_step")
    private Long totpLastStep;

    /** Incremented to invalidate outstanding access tokens. */
    @Column(name = "token_version")
    private Integer tokenVersion = 0;

    /** True after the first-login QR was shown once. Later sign-ins ask for the code only. */
    @Column(name = "totp_prompted")
    private Boolean totpPrompted = false;

    /** True after the user has entered a valid Google Authenticator code at least once. */
    @Column(name = "totp_confirmed")
    private Boolean totpConfirmed = false;

    /** Optional mobile number for SMS verification codes. */
    @Column(length = 32)
    private String phone;

    @Column(name = "must_change_password")
    private Boolean mustChangePassword = false;

    @Column(name = "tos_version_accepted")
    private String tosVersionAccepted;

    @Column(name = "tos_accepted_at")
    private LocalDateTime tosAcceptedAt;

    @Column(nullable = false)
    private boolean active = true;

    // Legacy fields for backward compatibility
    @NotBlank
    @Column(nullable = false)
    private String name; // Maps to fullName

    @NotBlank
    @Column(nullable = false)
    private String role; // Legacy role field - kept for backward compatibility

    @JsonIgnore
    @Column(name = "password_hash")
    private String passwordHash; // Legacy - use password instead

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @JsonIgnoreProperties({"users", "hibernateLazyInitializer", "handler"}) // Prevent circular reference
    private Set<Role> roles = new HashSet<>();

    public User() {}

    public User(String name, String role, String username, String passwordHash) {
        this.name = name;
        this.fullName = name;
        this.role = role;
        this.username = username;
        this.passwordHash = passwordHash;
        this.password = passwordHash; // For backward compatibility
    }

    @PostLoad
    public void postLoad() {
        // Sync password from passwordHash if password is null (for existing users)
        if (password == null && passwordHash != null) {
            password = passwordHash;
        }
        // Sync fullName from name if fullName is null
        if (fullName == null && name != null) {
            fullName = name;
        }
        // Sync name from fullName if name is null
        if (name == null && fullName != null) {
            name = fullName;
        }
        // Ensure mfaEnabled is not null
        if (mfaEnabled == null) {
            mfaEnabled = false;
        }
        if (totpPrompted == null) {
            totpPrompted = false;
        }
        if (totpConfirmed == null) {
            totpConfirmed = false;
        }
        if (mustChangePassword == null) {
            mustChangePassword = false;
        }
        if (tokenVersion == null) {
            tokenVersion = 0;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName != null ? fullName : name; }
    public void setFullName(String fullName) { 
        this.fullName = fullName;
        if (this.name == null) this.name = fullName; // Sync legacy field
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isMfaEnabled() { return mfaEnabled != null && mfaEnabled; }
    public Boolean getMfaEnabled() { return mfaEnabled; } // Getter for nullable Boolean
    public void setMfaEnabled(Boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; } // Accepts Boolean (nullable)

    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public boolean hasTotpSecret() { return totpSecret != null && !totpSecret.isBlank(); }

    public boolean isTotpPrompted() { return totpPrompted != null && totpPrompted; }
    public void setTotpPrompted(Boolean totpPrompted) { this.totpPrompted = totpPrompted; }

    public boolean isTotpConfirmed() { return totpConfirmed != null && totpConfirmed; }
    public void setTotpConfirmed(Boolean totpConfirmed) { this.totpConfirmed = totpConfirmed; }

    public Long getTotpLastStep() { return totpLastStep; }
    public void setTotpLastStep(Long totpLastStep) { this.totpLastStep = totpLastStep; }

    public int getTokenVersion() { return tokenVersion == null ? 0 : tokenVersion; }
    public void setTokenVersion(Integer tokenVersion) { this.tokenVersion = tokenVersion; }
    public void bumpTokenVersion() { this.tokenVersion = getTokenVersion() + 1; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isMustChangePassword() { return mustChangePassword != null && mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public String getTosVersionAccepted() { return tosVersionAccepted; }
    public void setTosVersionAccepted(String tosVersionAccepted) { this.tosVersionAccepted = tosVersionAccepted; }

    public LocalDateTime getTosAcceptedAt() { return tosAcceptedAt; }
    public void setTosAcceptedAt(LocalDateTime tosAcceptedAt) { this.tosAcceptedAt = tosAcceptedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    // Legacy getters/setters for backward compatibility
    public String getName() { return name != null ? name : fullName; }
    public void setName(String name) { 
        this.name = name;
        if (this.fullName == null) this.fullName = name; // Sync new field
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPasswordHash() { return passwordHash != null ? passwordHash : password; }
    public void setPasswordHash(String passwordHash) { 
        this.passwordHash = passwordHash;
        if (this.password == null) this.password = passwordHash; // Sync new field
    }
}

