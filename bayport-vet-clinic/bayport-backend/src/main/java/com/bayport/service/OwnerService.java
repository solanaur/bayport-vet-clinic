package com.bayport.service;

import com.bayport.entity.Owner;
import com.bayport.repository.AppointmentRepository;
import com.bayport.repository.OwnerRepository;
import com.bayport.repository.PetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Transactional
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;
    private final AppointmentRepository appointmentRepository;

    public OwnerService(OwnerRepository ownerRepository, 
                       PetRepository petRepository,
                       AppointmentRepository appointmentRepository) {
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public List<Owner> list() {
        return ownerRepository.findAll();
    }

    public Owner get(Long id) {
        return ownerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
    }

    public List<Owner> search(String term) {
        if (term == null || term.trim().isEmpty()) {
            return ownerRepository.findAll();
        }
        String pattern = "%" + term.trim() + "%";
        return ownerRepository.search(pattern);
    }

    public Owner create(Owner owner) {
        validateOwner(owner);
        return ownerRepository.save(owner);
    }

    public Owner update(Long id, Owner owner) {
        Owner existing = get(id);
        validateOwner(owner);
        existing.setFullName(owner.getFullName());
        existing.setPhone(owner.getPhone());
        existing.setEmail(owner.getEmail());
        existing.setAddress(owner.getAddress());
        return ownerRepository.save(existing);
    }

    public void delete(Long id) {
        Owner owner = get(id);
        
        // Check if owner has associated pets
        long petCount = petRepository.findAll().stream()
                .filter(p -> p.getOwnerId() != null && p.getOwnerId().equals(id))
                .count();
        
        if (petCount > 0) {
            throw new IllegalStateException(
                String.format("Cannot delete owner '%s' because they have %d associated pet(s). " +
                            "Please remove or reassign pets first.", owner.getFullName(), petCount));
        }
        
        // Check if owner has associated appointments
        long appointmentCount = appointmentRepository.findAll().stream()
                .filter(a -> owner.getFullName().equals(a.getOwner()))
                .count();
        
        if (appointmentCount > 0) {
            throw new IllegalStateException(
                String.format("Cannot delete owner '%s' because they have %d associated appointment(s). " +
                            "Please cancel or reassign appointments first.", owner.getFullName(), appointmentCount));
        }
        
        ownerRepository.deleteById(id);
    }

    private void validateOwner(Owner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("Owner payload is required");
        }
        if (!StringUtils.hasText(owner.getFullName())) {
            throw new IllegalArgumentException("Owner full name is required");
        }
        if (!StringUtils.hasText(owner.getPhone())) {
            throw new IllegalArgumentException("Owner phone is required");
        }
        // Enforce 11-digit numeric phone rule at the service level as well,
        // so that callers that do not rely on Bean Validation still get
        // a consistent 400-style error instead of persisting bad data.
        String phone = owner.getPhone().trim();
        if (!phone.matches("\\d{11}")) {
            throw new IllegalArgumentException("Owner phone must be exactly 11 digits (numbers only)");
        }
    }
}

