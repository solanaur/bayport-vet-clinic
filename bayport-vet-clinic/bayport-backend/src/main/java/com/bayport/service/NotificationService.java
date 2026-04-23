package com.bayport.service;

import com.bayport.entity.Notification;
import com.bayport.entity.User;
import com.bayport.repository.NotificationRepository;
import com.bayport.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Notifications run in {@link Propagation#REQUIRES_NEW} so a failure (e.g. DB constraint)
 * cannot mark the caller's transaction rollback-only — fixes prescriptions/sales saves
 * failing with UnexpectedRollbackException.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    private static final int MAX_MESSAGE_LEN = 500;

    private static String truncateMessage(String message) {
        if (message == null) return "";
        if (message.length() <= MAX_MESSAGE_LEN) return message;
        return message.substring(0, MAX_MESSAGE_LEN - 1) + "…";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyVetNewAppointment(String vetName, String ownerName, String petName) {
        try {
            List<User> allUsers = userRepository.findAll();
            allUsers.stream()
                .filter(user -> "vet".equalsIgnoreCase(user.getRole()))
                .filter(vet -> vet.getName().equalsIgnoreCase(vetName) || 
                             (vet.getFullName() != null && vet.getFullName().equalsIgnoreCase(vetName)) ||
                             vet.getUsername().equalsIgnoreCase(vetName))
                .forEach(vet -> {
                    Notification notif = new Notification();
                    notif.setUserId(vet.getId());
                    notif.setType("APPOINTMENT_NEW");
                    notif.setMessage(truncateMessage("New appointment created for " + ownerName + " - " + petName));
                    notificationRepository.save(notif);
                });
        } catch (Exception e) {
            // Log but don't fail
            System.err.println("Error notifying vet: " + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyReceptionistAppointmentApproved(String ownerName, String petName) {
        notifyFrontOffice("APPOINTMENT_APPROVED",
                "Appointment approved for " + ownerName + " - " + petName);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPharmacistNewPrescription(String petName, String ownerName) {
        notifyFrontOffice("PRESCRIPTION_NEW",
                "New prescription issued for " + petName + " (Owner: " + ownerName + ")");
    }

    /** Front Office = combined reception + pharmacy (cloud-friendly single role). */
    private void notifyFrontOffice(String type, String message) {
        try {
            final String safeMsg = truncateMessage(message);
            userRepository.findAll().stream()
                    .filter(this::isFrontOfficeStaff)
                    .forEach(u -> {
                        Notification notif = new Notification();
                        notif.setUserId(u.getId());
                        notif.setType(type);
                        notif.setMessage(safeMsg);
                        notificationRepository.save(notif);
                    });
        } catch (Exception e) {
            System.err.println("Error notifying Front Office: " + e.getMessage());
        }
    }

    private boolean isFrontOfficeStaff(User user) {
        if (user == null || user.getRole() == null) return false;
        String r = user.getRole().toLowerCase();
        return "front_office".equals(r) || "receptionist".equals(r) || "pharmacist".equals(r);
    }
}

