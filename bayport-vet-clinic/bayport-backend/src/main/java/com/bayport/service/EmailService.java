package com.bayport.service;

import com.bayport.entity.Appointment;
import com.bayport.entity.Pet;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final BrevoEmailClient brevo;
    private final NetlifyEmailRelayClient netlifyRelay;
    private final ResendEmailClient resend;
    private final String mailUsername;
    private final String mailPassword;
    private final boolean cloudPreferResend;
    private final String logoUrl;

    private static final String CLINIC_NAME = "BAYPORT VETERINARY CLINIC";
    private static final String CLINIC_NAME_TITLE = "Bayport Veterinary Clinic";
    private static final String CLINIC_ADDRESS = "322 Quirino Avenue, Brgy. Don Galo, Parañaque City";
    private static final String CLINIC_PHONE = "0968 633 2940";
    private static final String BRAND_PRIMARY = "#0057b8";

    @Autowired(required = false)
    public EmailService(
            JavaMailSender mailSender,
            BrevoEmailClient brevo,
            NetlifyEmailRelayClient netlifyRelay,
            ResendEmailClient resend,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${bayport.email.cloud-prefer-resend:true}") boolean cloudPreferResend,
            @Value("${bayport.email.logo-url:}") String logoUrl) {
        this.mailSender = mailSender;
        this.brevo = brevo;
        this.netlifyRelay = netlifyRelay;
        this.resend = resend;
        this.mailUsername = mailUsername == null ? "" : mailUsername.trim();
        this.mailPassword = mailPassword == null ? "" : mailPassword.trim();
        this.cloudPreferResend = cloudPreferResend;
        this.logoUrl = logoUrl == null ? "" : logoUrl.trim();
        if (usesBrevo()) {
            log.info("Email via Brevo API (from={} <{}>)", brevo.getFromName(), brevo.getFromEmail());
        } else if (usesNetlifyRelay()) {
            log.info("Email via Netlify relay (Brevo on Netlify)");
        } else if (usesResend() && !isResendSandbox()) {
            log.info("Email via Resend API (from={})", resend.getFrom());
        } else if (usesResend()) {
            log.warn("Email via Resend TEST sender — only your Resend account inbox receives mail. "
                    + "Set BREVO_API_KEY + BREVO_FROM_EMAIL on Render to email pet owners.");
        } else if (isSmtpConfigured()) {
            log.info("Email via SMTP for sender: {}", mailUsername);
        } else if (cloudPreferResend) {
            log.warn(
                    "Email not configured for cloud: Render FREE tier blocks SMTP. "
                            + "Set BREVO_API_KEY + BREVO_FROM_EMAIL (recommended) or RESEND_API_KEY with a verified domain.");
        } else {
            log.warn("Email (SMTP) not configured. Set SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD.");
        }
    }

    public boolean usesNetlifyRelay() {
        return netlifyRelay != null && netlifyRelay.isEnabled();
    }

    public boolean usesBrevo() {
        return brevo != null && brevo.isEnabled();
    }

    public boolean usesResend() {
        return resend != null && resend.isEnabled();
    }

    private boolean isSmtpConfigured() {
        return mailSender != null && !mailUsername.isEmpty() && !mailPassword.isEmpty();
    }

    /** True when Brevo, Netlify relay, verified Resend domain, or SMTP can reach any recipient. */
    public boolean canSendToAnyRecipient() {
        return usesBrevo() || usesNetlifyRelay() || (usesResend() && !isResendSandbox())
                || (isSmtpConfigured() && !cloudPreferResend);
    }

    /** True when an outbound email provider is configured (may still be Resend test-only). */
    public boolean isConfigured() {
        return usesBrevo() || usesNetlifyRelay() || usesResend() || isSmtpConfigured();
    }

    public String describeProvider() {
        if (usesBrevo()) {
            return "brevo";
        }
        if (usesNetlifyRelay()) {
            return "brevo-netlify";
        }
        if (usesResend()) {
            return isResendSandbox() ? "resend-test" : "resend";
        }
        if (isSmtpConfigured()) {
            return "smtp";
        }
        return "none";
    }

    /** True when using Resend's shared test sender — only delivers to your Resend account email. */
    public boolean isResendSandbox() {
        return usesResend() && resend.getFrom().toLowerCase().contains("onboarding@resend.dev");
    }

    public String getResendFrom() {
        return usesResend() ? resend.getFrom() : "";
    }

    public String resendSandboxNote() {
        if (canSendToAnyRecipient()) {
            return "";
        }
        if (isResendSandbox()) {
            return "Resend test sender blocks pet-owner email. Set Brevo on Netlify (BREVO_API_KEY, BREVO_FROM_EMAIL, "
                    + "BAYPORT_EMAIL_HOOK_SECRET) and the same BAYPORT_EMAIL_HOOK_SECRET on Render, then redeploy both.";
        }
        return "";
    }

    public String getBrevoFromEmail() {
        return usesBrevo() ? brevo.getFromEmail() : "";
    }

    /**
     * Branded layout: compact header (logo + clinic name), message body, signature footer.
     * Logo is loaded from a URL (not base64) so Gmail does not clip the email.
     */
    private String formatMessageAsHtml(String plainTextMessage, String subject) {
        String text = plainTextMessage == null ? "" : plainTextMessage.trim();
        String bodyHtml = formatBodyParagraphsHtml(text);

        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:12px;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:16px;line-height:1.65;color:#1a1a1a;background:#f4f6f8;\">"
                + "<div style=\"max-width:600px;margin:0 auto;background:#ffffff;"
                + "border-radius:8px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,0.08);\">"
                + buildEmailHeaderHtml()
                + "<div style=\"padding:22px 22px 10px;\">" + bodyHtml + "</div>"
                + buildEmailSignatureFooterHtml()
                + "</div></body></html>";
    }

    /** MariBank-style top bar: logo left, clinic name beside it on brand color. */
    private String buildEmailHeaderHtml() {
        return "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:"
                + BRAND_PRIMARY + ";\"><tr><td style=\"padding:16px 20px;\">"
                + "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\"><tr>"
                + "<td style=\"vertical-align:middle;padding-right:14px;\">"
                + buildLogoImgTag(52, CLINIC_NAME_TITLE)
                + "</td><td style=\"vertical-align:middle;\">"
                + "<span style=\"color:#ffffff;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:18px;font-weight:bold;line-height:1.25;\">Bayport</span><br>"
                + "<span style=\"color:rgba(255,255,255,0.92);font-size:13px;font-weight:600;"
                + "letter-spacing:0.3px;\">Veterinary Clinic</span>"
                + "</td></tr></table></td></tr></table>";
    }

    private String buildLogoImgTag(int sizePx, String alt) {
        if (logoUrl.isEmpty()) {
            return "<div style=\"width:" + sizePx + "px;height:" + sizePx + "px;background:#ffffff;"
                    + "border-radius:50%;text-align:center;line-height:" + sizePx + "px;"
                    + "color:" + BRAND_PRIMARY + ";font-weight:bold;font-size:20px;"
                    + "font-family:Arial,Helvetica,sans-serif;\">B</div>";
        }
        return "<img src=\"" + escapeHtml(logoUrl) + "\" alt=\"" + escapeHtml(alt) + "\" width=\"" + sizePx
                + "\" height=\"" + sizePx + "\" style=\"display:block;width:" + sizePx + "px;height:" + sizePx
                + "px;border-radius:50%;background:#ffffff;border:2px solid rgba(255,255,255,0.95);"
                + "object-fit:contain;\">";
    }

    private String formatBodyParagraphsHtml(String text) {
        if (text.isEmpty()) {
            return "<p style=\"margin:0 0 14px;\">&nbsp;</p>";
        }
        String[] blocks = text.split("\\n\\n+");
        StringBuilder sb = new StringBuilder();
        String paraStyle = "margin:0 0 16px;font-size:16px;line-height:1.65;color:#1a1a1a;";
        for (String block : blocks) {
            String line = escapeHtml(block).replace("\n", "<br/>");
            if (!line.isBlank()) {
                sb.append("<p style=\"").append(paraStyle).append("\">").append(line).append("</p>");
            }
        }
        return sb.toString();
    }

    private static String escapeHtml(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Bottom signature: small logo + clinic name + contact (like bank notification footers). */
    private String buildEmailSignatureFooterHtml() {
        return "<div style=\"border-top:1px solid #e0e0e0;padding:20px 22px 24px;text-align:center;\">"
                + "<p style=\"margin:0 0 18px;font-size:11px;color:#888888;line-height:1.4;\">"
                + "This is a system-generated message. Please do not reply to this email."
                + "</p>"
                + "<div style=\"margin:0 auto 12px;width:44px;\">"
                + buildLogoImgTag(44, CLINIC_NAME_TITLE)
                + "</div>"
                + "<p style=\"margin:0 0 6px;font-weight:bold;font-size:15px;color:" + BRAND_PRIMARY
                + ";letter-spacing:0.3px;\">" + CLINIC_NAME_TITLE + "</p>"
                + "<p style=\"margin:0;font-size:13px;color:#555555;line-height:1.5;\">" + CLINIC_ADDRESS + "</p>"
                + "<p style=\"margin:10px 0 0;font-size:13px;color:#555555;\">" + CLINIC_PHONE + "</p>"
                + "</div>";
    }

    private String formatMessageAsPlainText(String plainTextMessage) {
        String text = plainTextMessage == null ? "" : plainTextMessage.trim();
        return text + "\n\n"
                + CLINIC_NAME + "\n"
                + CLINIC_ADDRESS + "\n"
                + CLINIC_PHONE;
    }

    /** Generic email sender - now sends HTML emails */
    public void sendEmail(String to, String subject, String message) {
        if (to == null || to.trim().isEmpty()) {
            log.warn("Email not sent: recipient address is empty. Subject: {}", subject);
            throw new RuntimeException("Recipient email address is required");
        }

        String htmlContent = formatMessageAsHtml(message, subject);
        String plainContent = formatMessageAsPlainText(message);

        if (usesBrevo()) {
            try {
                brevo.sendHtmlEmail(to, subject, htmlContent, plainContent);
                log.info("Email sent successfully to: {}, Subject: {}", to, subject);
                return;
            } catch (RuntimeException e) {
                throw new RuntimeException("Failed to send email: " + toClientMessage(e), e);
            }
        }

        if (usesNetlifyRelay()) {
            try {
                netlifyRelay.sendHtmlEmail(to, subject, htmlContent, plainContent);
                log.info("Email sent via Netlify relay to: {}, Subject: {}", to, subject);
                return;
            } catch (RuntimeException e) {
                throw new RuntimeException("Failed to send email: " + toClientMessage(e), e);
            }
        }

        if (usesResend() && isResendSandbox()) {
            throw new IllegalStateException(
                    "Cannot send to " + to.trim() + " using Resend test mode. "
                            + "Configure Brevo on Netlify (BREVO_API_KEY, BREVO_FROM_EMAIL, BAYPORT_EMAIL_HOOK_SECRET) "
                            + "and set BAYPORT_EMAIL_HOOK_SECRET on Render, then redeploy.");
        }

        if (usesResend()) {
            try {
                resend.sendHtmlEmail(to, subject, htmlContent, plainContent);
                log.info("Email sent successfully to: {}, Subject: {}", to, subject);
                return;
            } catch (RuntimeException e) {
                throw new RuntimeException("Failed to send email: " + toClientMessage(e), e);
            }
        }

        if (!isSmtpConfigured()) {
            if (cloudPreferResend) {
                throw new RuntimeException(
                        "Email is not configured for cloud hosting. Render FREE tier blocks Gmail SMTP (ports 465/587). "
                                + "Create a free API key at https://resend.com and set RESEND_API_KEY in Render → Environment, then redeploy.");
            }
            throw new RuntimeException(
                    "Email is not configured. Set RESEND_API_KEY (cloud) or SPRING_MAIL_USERNAME + SPRING_MAIL_PASSWORD (local).");
        }

        if (cloudPreferResend) {
            throw new RuntimeException(
                    "Gmail SMTP cannot be used on Render FREE tier (connection times out). "
                            + "Set RESEND_API_KEY from https://resend.com in Render Environment instead.");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(to);
            try {
                helper.setFrom(mailUsername, "Bayport Veterinary Clinic");
            } catch (Exception fromEx) {
                helper.setFrom(mailUsername);
            }
            helper.setSubject(subject);
            helper.setText(plainContent, htmlContent);
            
            mailSender.send(mimeMessage);
            log.info("Email sent successfully to: {}, Subject: {}", to, subject);
        } catch (MessagingException e) {
            String errorMsg = extractUserFriendlyError(e);
            // Log full exception details for debugging
            log.error("Failed to send email to: {}, Subject: {}. Error: {}", to, subject, errorMsg);
            log.error("Full exception details:", e);
            if (e.getCause() != null) {
                log.error("Caused by: {}", e.getCause().getMessage());
            }
            throw new RuntimeException("Failed to send email: " + errorMsg, e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to: {}, Subject: {}. Error: {}", to, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts a user-friendly error message from email exceptions.
     */
    private String extractUserFriendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return "Unknown error occurred";
        }
        
        String lowerMsg = msg.toLowerCase();
        
        // Check for common error patterns
        if (lowerMsg.contains("authentication failed") || lowerMsg.contains("535") || 
            lowerMsg.contains("invalid login") || lowerMsg.contains("login failed")) {
            return "Email authentication failed. Please verify your Gmail app password in application.properties. " +
                   "Make sure you're using an app-specific password (not your regular Gmail password). " +
                   "Generate one at: https://myaccount.google.com/apppasswords";
        }
        if (lowerMsg.contains("connection") || lowerMsg.contains("could not connect") || lowerMsg.contains("timed out")) {
            return "Cannot reach Gmail SMTP from this server. On Render FREE tier, SMTP is blocked — use Resend: "
                    + "sign up at https://resend.com, add RESEND_API_KEY in Render Environment, redeploy.";
        }
        if (lowerMsg.contains("timeout")) {
            return "Email server connection timed out. On Render FREE tier use Resend (RESEND_API_KEY) instead of Gmail SMTP.";
        }
        if (lowerMsg.contains("invalid address") || lowerMsg.contains("invalid recipient")) {
            return "Invalid email address. Please check the recipient email.";
        }
        if (lowerMsg.contains("535") || lowerMsg.contains("authentication")) {
            return "Email authentication failed. Please check your Gmail username and app password in application.properties.";
        }
        
        // Return a cleaned version of the error
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    /** User-facing message for API responses and UI toasts. */
    public String toClientMessage(Throwable error) {
        if (error == null) {
            return "Email delivery failed.";
        }
        String msg = error.getMessage();
        Throwable cause = error.getCause();
        while ((msg == null || msg.isBlank()) && cause != null) {
            msg = cause.getMessage();
            cause = cause.getCause();
        }
        if (msg == null || msg.isBlank()) {
            return "Email delivery failed.";
        }
        msg = msg.replaceFirst("^(Failed to send email:\\s*)+", "")
                .replaceFirst("^(Resend failed:\\s*)+", "");
        String lower = msg.toLowerCase();
        if (lower.contains("only send") && lower.contains("your own email")) {
            return "Resend test mode blocks delivery to pet owners. "
                    + "Set BREVO_API_KEY + BREVO_FROM_EMAIL on Render (brevo.com — verify sender email), "
                    + "or verify a domain at resend.com/domains and set RESEND_FROM.";
        }
        if (lower.contains("brevo failed")) {
            if (lower.contains("sender") || lower.contains("not verified")) {
                return "Brevo sender not verified. In brevo.com → Senders, verify "
                        + "BREVO_FROM_EMAIL, then try again.";
            }
            return msg.length() > 280 ? msg.substring(0, 280) + "…" : msg;
        }
        if (lower.contains("resend failed") || lower.contains("validation_error")) {
            return extractUserFriendlyError(new Exception(msg));
        }
        return extractUserFriendlyError(new Exception(msg));
    }

    /** Old controllers expect this name */
    public void send(String to, String subject, String message) {
        sendEmail(to, subject, message);
    }

    /** Old controllers expect this signature */
    public void sendSimpleEmail(String to, String subject, String message) {
        sendEmail(to, subject, message);
    }

    /** For BayportService — Appointment reminder (sent when vet approves) */
    public void sendAppointmentReminder(String to, Appointment appt) {
        if (appt == null) {
            log.warn("Cannot send appointment reminder: appointment is null");
            return;
        }
        
        // Get accurate pet data - ensure no "Unknown" values
        String petName = "Pet";
        String petSpecies = "";
        String petBreed = "";
        String petAge = "";
        String petGender = "";
        
        if (appt.getPet() != null) {
            Pet pet = appt.getPet();
            petName = (pet.getName() != null && !pet.getName().trim().isEmpty()) ? pet.getName() : "Pet";
            petSpecies = (pet.getSpecies() != null && !pet.getSpecies().trim().isEmpty()) ? pet.getSpecies() : "";
            petBreed = (pet.getBreed() != null && !pet.getBreed().trim().isEmpty()) ? pet.getBreed() : "";
            petAge = (pet.getAge() != null) ? String.valueOf(pet.getAge()) + " years old" : "";
            petGender = (pet.getGender() != null && !pet.getGender().trim().isEmpty()) ? pet.getGender() : "";
        }
        
        String dateStr = appt.getDate() != null ? appt.getDate().toString() : "TBD";
        String timeStr = appt.getTime() != null ? appt.getTime() : "TBD";
        String vetStr = appt.getVet() != null ? appt.getVet() : "TBD";
        String codeStr = appt.getCode() != null ? appt.getCode() : "";
        
        // Build detailed pet information
        StringBuilder petInfo = new StringBuilder(petName);
        if (!petSpecies.isEmpty()) {
            petInfo.append(" (").append(petSpecies);
            if (!petBreed.isEmpty()) {
                petInfo.append(" - ").append(petBreed);
            }
            petInfo.append(")");
        }
        if (!petAge.isEmpty()) {
            petInfo.append(", ").append(petAge);
        }
        if (!petGender.isEmpty()) {
            petInfo.append(", ").append(petGender);
        }
        
        String subject = "Appointment Approved - Bayport Veterinary Clinic";
        String message = String.format(
            "Hello!\n\n" +
            "Your appointment has been approved by the veterinarian:\n\n" +
            "Appointment Code: %s\n" +
            "Date: %s\n" +
            "Time: %s\n" +
            "Pet: %s\n" +
            "Veterinarian: %s\n\n" +
            "Please arrive on time. If you need to reschedule, please contact us.\n\n" +
            "Thank you!",
            codeStr,
            dateStr,
            timeStr,
            petInfo.toString(),
            vetStr
        );
        sendEmail(to, subject, message);
    }
}
