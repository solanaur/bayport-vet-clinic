package com.bayport.security;

import com.bayport.entity.Appointment;
import com.bayport.entity.Pet;
import com.bayport.entity.User;
import com.bayport.repository.PetRepository;
import com.bayport.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RecordAccessServiceTest {

    @Mock UserRepository userRepository;
    @Mock PetRepository petRepository;

    @Test
    void veterinariansSharePetRecords() {
        RecordAccessService svc = new RecordAccessService(userRepository, petRepository);
        User vetA = vet(1L, "vetA", "Dr. Arla");
        User vetB = vet(2L, "vetB", "Dr. Ben");

        Pet pet = new Pet();
        pet.setId(10L);
        pet.setAssignedVeterinarianId(1L);

        assertTrue(svc.canAccessPet(vetA, pet));
        assertTrue(svc.canAccessPet(vetB, pet));
    }

    @Test
    void veterinarianSeesOnlyAppointmentsAssignedToThem() {
        RecordAccessService svc = new RecordAccessService(userRepository, petRepository);
        User vetA = vet(1L, "vetA", "Dr. Arla");
        User vetB = vet(2L, "vetB", "Dr. Ben");

        Appointment arla = new Appointment();
        arla.setVet("Dr. Arla");
        arla.setPetId(10L);
        Appointment ben = new Appointment();
        ben.setVet("Dr. Ben");
        ben.setPetId(10L);

        assertTrue(svc.canAccessAppointment(vetA, arla));
        assertFalse(svc.canAccessAppointment(vetA, ben));
        assertTrue(svc.canAccessAppointment(vetB, ben));
        assertFalse(svc.canAccessAppointment(vetB, arla));
    }

    @Test
    void adminCanAccessAnyPet() {
        RecordAccessService svc = new RecordAccessService(userRepository, petRepository);
        User admin = new User();
        admin.setId(99L);
        admin.setUsername("admin");
        admin.setRole("admin");
        Pet pet = new Pet();
        pet.setId(10L);
        pet.setAssignedVeterinarianId(1L);
        assertTrue(svc.canAccessPet(admin, pet));
    }

    private static User vet(Long id, String username, String name) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setName(name);
        user.setRole("vet");
        return user;
    }
}
