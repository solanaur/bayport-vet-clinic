package com.bayport.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String plainPassword; // For password input only

    @Column(name = "display_password")
    @JsonIgnore
    private String displayPassword; // Plaintext password for admin viewing only

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

    public String getPlainPassword() { return plainPassword; }
    public void setPlainPassword(String plainPassword) { this.plainPassword = plainPassword; }

    public String getDisplayPassword() { return displayPassword; }
    public void setDisplayPassword(String displayPassword) { this.displayPassword = displayPassword; }
}

