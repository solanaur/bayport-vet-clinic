package com.bayport.security;

import com.bayport.entity.Pet;
import com.bayport.entity.User;
import com.bayport.repository.AppointmentRepository;
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
    @Mock AppointmentRepository appointmentRepository;

    @Test
    void veterinarianCannotAccessOtherVetAssignedPet() {
        RecordAccessService svc = new RecordAccessService(userRepository, petRepository, appointmentRepository);
        User vetA = new User();
        vetA.setId(1L);
        vetA.setUsername("vetA");
        vetA.setRole("vet");
        User vetB = new User();
        vetB.setId(2L);
        vetB.setUsername("vetB");
        vetB.setRole("vet");

        Pet pet = new Pet();
        pet.setId(10L);
        pet.setAssignedVeterinarianId(1L);

        assertTrue(svc.canAccessPet(vetA, pet));
        assertFalse(svc.canAccessPet(vetB, pet));
    }

    @Test
    void adminCanAccessAnyPet() {
        RecordAccessService svc = new RecordAccessService(userRepository, petRepository, appointmentRepository);
        User admin = new User();
        admin.setId(99L);
        admin.setUsername("admin");
        admin.setRole("admin");
        Pet pet = new Pet();
        pet.setId(10L);
        pet.setAssignedVeterinarianId(1L);
        assertTrue(svc.canAccessPet(admin, pet));
    }
}
