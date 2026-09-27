package com.bayport.security;

import com.bayport.entity.Appointment;
import com.bayport.entity.Pet;
import com.bayport.entity.Prescription;
import com.bayport.entity.User;
import com.bayport.repository.AppointmentRepository;
import com.bayport.repository.PetRepository;
import com.bayport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend record-level authorization. Never trust client-supplied vet IDs.
 */
@Service
public class RecordAccessService {

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final AppointmentRepository appointmentRepository;

    public RecordAccessService(
            UserRepository userRepository,
            PetRepository petRepository,
            AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.petRepository = petRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
    }

    public User requireCurrentUser() {
        return currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    public boolean isAdmin(User user) {
        return SecurityUtils.isAdminUser(user);
    }

    public boolean isFrontOffice(User user) {
        if (user == null) {
            return false;
        }
        String legacy = user.getRole() == null ? "" : user.getRole().trim().toLowerCase(Locale.ROOT);
        if ("front_office".equals(legacy) || "receptionist".equals(legacy) || "pharmacist".equals(legacy)
                || "staff".equals(legacy)) {
            return true;
        }
        if (user.getRoles() != null) {
            return user.getRoles().stream().anyMatch(r -> {
                if (r == null || r.getName() == null) return false;
                String n = r.getName().toUpperCase(Locale.ROOT);
                return n.contains("FRONT_OFFICE") || n.contains("RECEPTIONIST") || n.contains("PHARMACIST")
                        || n.equals("ROLE_STAFF") || n.equals("STAFF");
            });
        }
        return false;
    }

    public boolean isVeterinarian(User user) {
        if (user == null) {
            return false;
        }
        String legacy = user.getRole() == null ? "" : user.getRole().trim().toLowerCase(Locale.ROOT);
        if ("vet".equals(legacy) || "veterinarian".equals(legacy)) {
            return true;
        }
        if (user.getRoles() != null) {
            return user.getRoles().stream().anyMatch(r -> {
                if (r == null || r.getName() == null) return false;
                String n = r.getName().toUpperCase(Locale.ROOT);
                return n.contains("VET") || n.contains("VETERINARIAN");
            });
        }
        return false;
    }

    public boolean canAccessPet(User user, Pet pet) {
        if (user == null || pet == null) {
            return false;
        }
        if (isAdmin(user) || isFrontOffice(user)) {
            return true;
        }
        if (!isVeterinarian(user)) {
            return false;
        }
        Long assignedId = pet.getAssignedVeterinarianId();
        if (assignedId != null) {
            return Objects.equals(assignedId, user.getId());
        }
        // Legacy/unassigned pets: allow if this vet has any appointment for the pet
        return hasAppointmentWithVet(pet.getId(), user);
    }

    public void requirePetAccess(Long petId) {
        User user = requireCurrentUser();
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        if (!canAccessPet(user, pet)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    public boolean canAccessAppointment(User user, Appointment appointment) {
        if (user == null || appointment == null) {
            return false;
        }
        if (isAdmin(user) || isFrontOffice(user)) {
            return true;
        }
        if (!isVeterinarian(user)) {
            return false;
        }
        if (isVetAssignedToAppointment(user, appointment.getVet())) {
            return true;
        }
        if (appointment.getPetId() != null) {
            return petRepository.findById(appointment.getPetId())
                    .map(p -> canAccessPet(user, p))
                    .orElse(false);
        }
        return false;
    }

    public void requireAppointmentAccess(Appointment appointment) {
        User user = requireCurrentUser();
        if (!canAccessAppointment(user, appointment)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    public boolean canAccessPrescription(User user, Prescription rx) {
        if (user == null || rx == null) {
            return false;
        }
        if (isAdmin(user) || isFrontOffice(user)) {
            return true;
        }
        if (!isVeterinarian(user)) {
            return false;
        }
        if (rx.getPetId() != null) {
            return petRepository.findById(rx.getPetId())
                    .map(p -> canAccessPet(user, p))
                    .orElse(false);
        }
        // Fall back to prescriber name match when pet is missing
        return namesMatch(rx.getPrescriber(), user.getName())
                || namesMatch(rx.getPrescriber(), user.getFullName())
                || namesMatch(rx.getPrescriber(), user.getUsername());
    }

    public void requirePrescriptionAccess(Prescription rx) {
        User user = requireCurrentUser();
        if (!canAccessPrescription(user, rx)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    public List<Pet> filterPets(List<Pet> pets) {
        User user = requireCurrentUser();
        if (isAdmin(user) || isFrontOffice(user)) {
            return pets;
        }
        return pets.stream().filter(p -> canAccessPet(user, p)).toList();
    }

    public List<Appointment> filterAppointments(List<Appointment> appointments) {
        User user = requireCurrentUser();
        if (isAdmin(user) || isFrontOffice(user)) {
            return appointments;
        }
        return appointments.stream().filter(a -> canAccessAppointment(user, a)).toList();
    }

    public List<Prescription> filterPrescriptions(List<Prescription> list) {
        User user = requireCurrentUser();
        if (isAdmin(user) || isFrontOffice(user)) {
            return list;
        }
        return list.stream().filter(rx -> canAccessPrescription(user, rx)).toList();
    }

    public boolean isVetAssignedToAppointment(User vetUser, String appointmentVet) {
        if (vetUser == null || appointmentVet == null || appointmentVet.isBlank()) {
            return false;
        }
        return namesMatch(appointmentVet, vetUser.getName())
                || namesMatch(appointmentVet, vetUser.getFullName())
                || namesMatch(appointmentVet, vetUser.getUsername());
    }

    private boolean hasAppointmentWithVet(Long petId, User vet) {
        if (petId == null || vet == null) {
            return false;
        }
        return appointmentRepository.findAll().stream()
                .filter(a -> Objects.equals(a.getPetId(), petId))
                .anyMatch(a -> isVetAssignedToAppointment(vet, a.getVet()));
    }

    private static boolean namesMatch(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        String a = left.trim();
        String b = right.trim();
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equalsIgnoreCase(b)) {
            return true;
        }
        String normLeft = a.toLowerCase(Locale.ROOT).replaceAll("^dr\\.?\\s*", "").replaceAll("\\s+", " ").trim();
        String normRight = b.toLowerCase(Locale.ROOT).replaceAll("^dr\\.?\\s*", "").replaceAll("\\s+", " ").trim();
        return !normLeft.isEmpty() && normLeft.equals(normRight);
    }
}
