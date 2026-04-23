package com.bayport.service;

import com.bayport.entity.Appointment;
import com.bayport.entity.Pet;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private String logoBase64 = null;

    @Autowired(required = false)
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        if (mailSender == null) {
            log.warn("JavaMailSender not configured. Email notifications will be disabled. " +
                    "To enable, configure SMTP settings in application.properties");
        } else {
            // Load logo once at startup
            loadLogo();
        }
    }

    /**
     * Loads the logo from assets/logo.png and converts it to base64 for email embedding.
     */
    private void loadLogo() {
        try {
            byte[] logoBytes = null;
            
            // First, try classpath (most reliable for packaged JAR)
            try {
                Resource resource = new ClassPathResource("assets/logo.png");
                if (resource.exists() && resource.isReadable()) {
                    logoBytes = StreamUtils.copyToByteArray(resource.getInputStream());
                    log.info("Logo loaded from classpath: assets/logo.png ({} bytes)", logoBytes.length);
                }
            } catch (Exception e) {
                log.debug("Logo not found in classpath: {}", e.getMessage());
            }

            // If not found in classpath, try file system paths
            if (logoBytes == null) {
                Path[] possiblePaths = {
                    Paths.get("src/main/resources/assets/logo.png"),
                    Paths.get("assets/logo.png"),
                    Paths.get("../assets/logo.png"),
                    Paths.get("../../assets/logo.png"),
                    Paths.get("bayport-vet-clinic/assets/logo.png")
                };

                for (Path path : possiblePaths) {
                    try {
                        if (Files.exists(path) && Files.isReadable(path)) {
                            logoBytes = Files.readAllBytes(path);
                            log.info("Logo loaded from file system: {} ({} bytes)", path.toAbsolutePath(), logoBytes.length);
                            break;
                        }
                    } catch (Exception e) {
                        log.debug("Could not read logo from {}: {}", path, e.getMessage());
                    }
                }
            }

            if (logoBytes != null && logoBytes.length > 0) {
                logoBase64 = Base64.getEncoder().encodeToString(logoBytes);
                log.info("Logo successfully encoded to base64. Base64 length: {} characters", logoBase64.length());
            } else {
                log.error("Logo file not found in any location. Emails will be sent without logo.");
                log.error("Please ensure logo.png exists in: src/main/resources/assets/logo.png");
            }
        } catch (Exception e) {
            log.error("Failed to load logo: {}", e.getMessage(), e);
        }
    }

    /**
     * Builds the HTML email footer with clinic name, address, and contact.
     */
    private String buildEmailFooter() {
        StringBuilder footer = new StringBuilder();
        footer.append("<div style=\"margin-top: 30px; padding-top: 20px; border-top: 2px solid #e0e0e0; text-align: center; font-family: Arial, sans-serif; background-color: #f9f9f9; padding: 20px;\">");
        
        // Clinic name (bold)
        footer.append("<p style=\"margin: 10px 0 5px 0; font-size: 18px; font-weight: bold; color: #2c3e50; letter-spacing: 0.5px;\">")
              .append("BAYPORT VETERINARY CLINIC")
              .append("</p>");
        
        // Address
        footer.append("<p style=\"margin: 5px 0; font-size: 13px; color: #555; line-height: 1.5;\">")
              .append("322 Quirino Avenue, Brgy. Don Galo, Parañaque City")
              .append("</p>");
        
        // Contact number
        footer.append("<p style=\"margin: 5px 0 0 0; font-size: 13px; color: #555; line-height: 1.5;\">")
              .append("0968 633 2940")
              .append("</p>");
        
        footer.append("</div>");
        return footer.toString();
    }

    /**
     * Converts plain text message to HTML format with footer.
     */
    private String formatMessageAsHtml(String plainTextMessage) {
        if (plainTextMessage == null) {
            plainTextMessage = "";
        }
        
        // Convert newlines to HTML line breaks
        String htmlBody = plainTextMessage
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n\n", "</p><p style=\"margin: 10px 0;\">")
            .replace("\n", "<br/>");
        
        // Wrap in paragraph tags
        if (!htmlBody.startsWith("<p")) {
            htmlBody = "<p style=\"margin: 10px 0;\">" + htmlBody + "</p>";
        }
        
        // Build complete HTML email
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        html.append("</head>");
        html.append("<body style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;\">");
        html.append("<div style=\"background-color: #ffffff;\">");
        html.append(htmlBody);
        html.append(buildEmailFooter());
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }

    /** Generic email sender - now sends HTML emails */
    public void sendEmail(String to, String subject, String message) {
        if (mailSender == null) {
            log.warn("Email not sent to {}: JavaMailSender not configured. Subject: {}", to, subject);
            throw new RuntimeException("Email service is not configured. Please configure SMTP settings in application.properties");
        }
        
        if (to == null || to.trim().isEmpty()) {
            log.warn("Email not sent: recipient address is empty. Subject: {}", subject);
            throw new RuntimeException("Recipient email address is required");
        }
        
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            
            // Convert message to HTML and add footer
            String htmlContent = formatMessageAsHtml(message);
            helper.setText(htmlContent, true); // true = HTML content
            
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
        if (lowerMsg.contains("connection") || lowerMsg.contains("could not connect")) {
            return "Cannot connect to email server. Please check your internet connection and SMTP settings.";
        }
        if (lowerMsg.contains("timeout")) {
            return "Email server connection timed out. Please try again later.";
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
