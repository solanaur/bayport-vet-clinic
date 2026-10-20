package com.bayport.security;

/**
 * Removes passwords, one-time codes, tokens, and TOTP secrets from text before it is logged.
 */
public final class LogScrubber {

    private LogScrubber() {}

    public static String scrub(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String scrubbed = message;
        scrubbed = scrubbed.replaceAll("(?i)bearer\\s+[A-Za-z0-9\\-._~+/]+=*", "Bearer [REDACTED]");
        scrubbed = scrubbed.replaceAll(
                "(?i)(password|passwd|pwd|otp|localotp|token|jwt|mfatoken|mfasetuptoken|totp|totp_secret|totpsecret|secret|authorization|api[_-]?key)(\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|\\S+)",
                "$1$2[REDACTED]");
        return scrubbed;
    }
}
