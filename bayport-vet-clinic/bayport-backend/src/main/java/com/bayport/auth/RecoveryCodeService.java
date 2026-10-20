package com.bayport.auth;

import com.bayport.entity.MfaRecoveryCode;
import com.bayport.entity.User;
import com.bayport.repository.MfaRecoveryCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecoveryCodeService {

    private final MfaRecoveryCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public RecoveryCodeService(MfaRecoveryCodeRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public List<String> replaceAll(User user) {
        repository.deleteByUserId(user.getId());
        List<String> codes = RecoveryCodes.generate(random);
        LocalDateTime now = LocalDateTime.now();
        for (String code : codes) {
            MfaRecoveryCode row = new MfaRecoveryCode();
            row.setUser(user);
            row.setCodeHash(passwordEncoder.encode(RecoveryCodes.normalize(code)));
            row.setCreatedAt(now);
            repository.save(row);
        }
        return codes;
    }

    @Transactional
    public boolean consume(User user, String presented) {
        String normalized = RecoveryCodes.normalize(presented);
        if (normalized.length() < 8 || user.getId() == null) {
            return false;
        }
        for (MfaRecoveryCode row : repository.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (passwordEncoder.matches(normalized, row.getCodeHash())) {
                row.setUsedAt(LocalDateTime.now());
                repository.save(row);
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void deleteAll(Long userId) {
        if (userId != null) {
            repository.deleteByUserId(userId);
        }
    }
}
