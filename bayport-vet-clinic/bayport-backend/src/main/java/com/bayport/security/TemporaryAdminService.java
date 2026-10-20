package com.bayport.security;

import com.bayport.entity.Role;
import com.bayport.entity.User;
import com.bayport.repository.MfaCodeRepository;
import com.bayport.repository.MfaRecoveryCodeRepository;
import com.bayport.repository.RoleRepository;
import com.bayport.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a temporary admin / admin123 sign-in available until a real administrator
 * has been saved. The real account is a normal database row and is never deleted here.
 */
@Service
public class TemporaryAdminService {

    private static final String FACTORY_PASSWORD = "admin123";
    private static final List<String> FACTORY_USERNAMES = List.of("admin", "vet", "frontdesk", "recept", "pharm");
    private static final List<String> FACTORY_PASSWORDS = List.of("admin123", "vet123", "frontdesk123");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaCodeRepository mfaCodeRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;

    public TemporaryAdminService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            MfaCodeRepository mfaCodeRepository,
            MfaRecoveryCodeRepository recoveryCodeRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mfaCodeRepository = mfaCodeRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    @Transactional
    public void prepareStartup() {
        if (hasDurableAdminBesidesTemporary()) {
            retireFactoryAccounts();
            return;
        }
        ensureTemporaryAdmin();
    }

    @Transactional
    public List<String> retireFactoryAccounts() {
        if (!hasDurableAdmin()) {
            return List.of();
        }
        List<String> removed = new ArrayList<>();
        for (String username : FACTORY_USERNAMES) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || !usesFactoryPassword(user)) {
                continue;
            }
            mfaCodeRepository.deleteByUser(user);
            if (user.getId() != null) {
                recoveryCodeRepository.deleteByUserId(user.getId());
            }
            removed.add(user.getUsername());
            userRepository.delete(user);
        }
        return removed;
    }

    public boolean bypassLoginAllowed(String username, String password) {
        return TemporaryAdminRules.isTemporaryLogin(username, password) && !hasDurableAdmin();
    }

    public boolean hasDurableAdmin() {
        return userRepository.findAll().stream().anyMatch(user ->
                TemporaryAdminRules.isDurableAdmin(user.getRole(), usesFactoryPassword(user)));
    }

    private boolean hasDurableAdminBesidesTemporary() {
        return userRepository.findAll().stream().anyMatch(user -> {
            if (user.getUsername() != null
                    && TemporaryAdminRules.USERNAME.equalsIgnoreCase(user.getUsername().trim())) {
                return false;
            }
            return TemporaryAdminRules.isDurableAdmin(user.getRole(), usesFactoryPassword(user));
        });
    }

    private void ensureTemporaryAdmin() {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        User admin = userRepository.findByUsername(TemporaryAdminRules.USERNAME).orElse(null);
        if (admin == null) {
            admin = new User("Administrator", "admin", TemporaryAdminRules.USERNAME, passwordEncoder.encode(FACTORY_PASSWORD));
            admin.setEmail("admin@localhost");
        } else {
            String encoded = passwordEncoder.encode(FACTORY_PASSWORD);
            admin.setPassword(encoded);
            admin.setPasswordHash(encoded);
            admin.setRole("admin");
        }
        admin.setActive(true);
        admin.setMfaEnabled(false);
        admin.setMustChangePassword(false);
        admin.setTotpConfirmed(false);
        if (admin.getRoles() != null) {
            admin.getRoles().add(adminRole);
        }
        userRepository.save(admin);
    }

    private boolean usesFactoryPassword(User user) {
        String hash = user.getPassword() != null ? user.getPassword() : user.getPasswordHash();
        if (hash == null || hash.isBlank()) {
            return false;
        }
        for (String factory : FACTORY_PASSWORDS) {
            try {
                if (passwordEncoder.matches(factory, hash)) {
                    return true;
                }
            } catch (Exception ignored) {
                // Stored value is not a hash this encoder understands.
            }
        }
        return false;
    }
}
