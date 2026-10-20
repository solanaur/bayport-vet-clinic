package com.bayport.config;

import com.bayport.auth.TotpEnrollmentService;
import com.bayport.entity.User;
import com.bayport.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rewrites leftover plaintext authenticator secrets so they are stored encrypted.
 */
@Component
@Order(2)
public class TotpSecretMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TotpSecretMigration.class);

    private final UserRepository userRepository;
    private final TotpEnrollmentService enrollmentService;

    public TotpSecretMigration(UserRepository userRepository, TotpEnrollmentService enrollmentService) {
        this.userRepository = userRepository;
        this.enrollmentService = enrollmentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int updated = 0;
        for (User user : userRepository.findAll()) {
            String stored = user.getTotpSecret();
            if (stored == null || stored.isBlank() || enrollmentService.isEncrypted(stored)) {
                continue;
            }
            user.setTotpSecret(enrollmentService.encryptExisting(stored));
            userRepository.save(user);
            updated++;
        }
        if (updated > 0) {
            log.info("Encrypted {} existing authenticator secret(s)", updated);
        }
    }
}
