package com.bayport.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Gmail SMTP tuned for cloud hosts (Render). Port 465 + SSL is more reliable than 587 on some networks.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.mail.username", matchIfMissing = false)
public class MailConfig {

    /** Gmail SMTP host — never use the clinic email address as the host. */
    private static final String GMAIL_SMTP_HOST = "smtp.gmail.com";

    @Bean
    @Primary
    public JavaMailSender javaMailSender(
            @Value("${spring.mail.host:}") String configuredHost,
            @Value("${spring.mail.port:465}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {
        String host = resolveSmtpHost(configuredHost);
        if (configuredHost != null && !configuredHost.isBlank() && !host.equals(configuredHost.trim())) {
            log.warn(
                    "Ignoring invalid spring.mail.host / SPRING_MAIL_HOST={} — use smtp.gmail.com as host and "
                            + "bayportveterinaryclinic@gmail.com as SPRING_MAIL_USERNAME only",
                    configuredHost);
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username == null ? "" : username.trim());
        sender.setPassword(password == null ? "" : password.trim());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "30000");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");
        props.put("mail.smtp.ssl.trust", GMAIL_SMTP_HOST);

        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        }

        log.info("JavaMailSender ready: host={} port={} user={}", host, port, sender.getUsername());
        return sender;
    }

    static String resolveSmtpHost(String configuredHost) {
        if (configuredHost == null || configuredHost.isBlank()) {
            return GMAIL_SMTP_HOST;
        }
        String h = configuredHost.trim();
        if (h.contains("@") || h.contains("gmail.com") && !h.startsWith("smtp.")) {
            return GMAIL_SMTP_HOST;
        }
        return h;
    }
}
