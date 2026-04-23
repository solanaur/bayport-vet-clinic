package com.bayport.repository;

import com.bayport.entity.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PetRepository extends JpaRepository<Pet, Long> {
    List<Pet> findByOwnerId(Long ownerId);
    
    // Soft delete queries
    List<Pet> findByDeletedFalse();
    List<Pet> findByDeletedTrue();
    Optional<Pet> findByIdAndDeletedFalse(Long id);

    /** Microchip lookup (case-insensitive); excludes soft-deleted pets. */
    Optional<Pet> findFirstByMicrochipIgnoreCaseAndDeletedFalse(String microchip);
}

