package com.bayport.security;

import com.bayport.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CustomUserDetails implements UserDetails {
    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            authorities.addAll(
                    user.getRoles().stream()
                            .filter(role -> role != null && role.getName() != null && !role.getName().isBlank())
                            .map(role -> new SimpleGrantedAuthority(SecurityUtils.normalizeRoleAuthority(role.getName())))
                            .collect(Collectors.toSet()));
        }
        if (user.getRole() != null && !user.getRole().isBlank()) {
            authorities.add(new SimpleGrantedAuthority(SecurityUtils.normalizeRoleAuthority(user.getRole())));
        }
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        // Use password if available, otherwise fallback to passwordHash for backward compatibility
        String pwd = user.getPassword();
        if (pwd == null || pwd.isEmpty()) {
            pwd = user.getPasswordHash();
        }
        return pwd;
    }

    @Override
    public String getUsername() {
        return user.getUsername();
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

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }

    public User getUser() {
        return user;
    }
}
