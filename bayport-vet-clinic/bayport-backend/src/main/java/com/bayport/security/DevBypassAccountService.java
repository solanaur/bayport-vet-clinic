package com.bayport.security;

import com.bayport.entity.Role;
import com.bayport.entity.User;
import com.bayport.repository.MfaCodeRepository;
import com.bayport.repository.MfaRecoveryCodeRepository;
import com.bayport.repository.RoleRepository;
import com.bayport.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the three local bypass accounts on or off. Off is the default, so a clinic
 * install does not keep admin123, vet123, or front123.
 */
@Service
public class DevBypassAccountService {

    private final boolean enabled;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaCodeRepository mfaCodeRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;

    public DevBypassAccountService(
            @Value("${bayport.dev.bypass-logins:false}") boolean enabled,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            MfaCodeRepository mfaCodeRepository,
            MfaRecoveryCodeRepository recoveryCodeRepository) {
        this.enabled = enabled;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mfaCodeRepository = mfaCodeRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    public boolean allows(String username, String password) {
        return enabled && DevBypassAccounts.matches(username, password);
    }

    @Transactional
    public void sync() {
        if (enabled) {
            for (DevBypassAccounts.Account account : DevBypassAccounts.ACCOUNTS) {
                ensure(account);
            }
            System.out.println("[DevBypass] Local accounts admin, vet, and front can sign in without Google Authenticator.");
            return;
        }
        for (DevBypassAccounts.Account account : DevBypassAccounts.ACCOUNTS) {
            if ("admin".equals(account.username())) {
                continue;
            }
            removeIfStillUsingBypassPassword(account);
        }
    }

    private void ensure(DevBypassAccounts.Account account) {
        Role role = roleRepository.findByName(account.authority())
                .orElseGet(() -> roleRepository.save(new Role(account.authority())));
        User user = userRepository.findByUsername(account.username()).orElse(null);
        String encoded = passwordEncoder.encode(account.password());
        if (user == null) {
            user = new User(account.name(), account.role(), account.username(), encoded);
        } else {
            user.setName(account.name());
            user.setFullName(account.name());
            user.setRole(account.role());
            user.setPassword(encoded);
            user.setPasswordHash(encoded);
        }
        user.setEmail(account.username() + "@localhost");
        user.setActive(true);
        user.setMfaEnabled(false);
        user.setMustChangePassword(false);
        user.setTotpConfirmed(false);
        user.setTotpPrompted(false);
        user.setTotpSecret(null);
        user.setTotpLastStep(null);
        if (user.getRoles() == null) {
            user.setRoles(new java.util.HashSet<>());
        } else {
            user.getRoles().clear();
        }
        user.getRoles().add(role);
        userRepository.save(user);
    }

    private void removeIfStillUsingBypassPassword(DevBypassAccounts.Account account) {
        User user = userRepository.findByUsername(account.username()).orElse(null);
        if (user == null) {
            return;
        }
        String hash = user.getPassword() != null ? user.getPassword() : user.getPasswordHash();
        if (hash == null || hash.isBlank()) {
            return;
        }
        boolean matches;
        try {
            matches = passwordEncoder.matches(account.password(), hash);
        } catch (Exception ignored) {
            return;
        }
        if (!matches) {
            return;
        }
        mfaCodeRepository.deleteByUser(user);
        if (user.getId() != null) {
            recoveryCodeRepository.deleteByUserId(user.getId());
        }
        userRepository.delete(user);
    }
}
