package com.bayport.web;

import com.bayport.entity.Pet;
import com.bayport.service.PetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/recycle-bin/pets")
@PreAuthorize("hasRole('ADMIN')")
public class PetRecycleBinController {

    private final PetService petService;

    public PetRecycleBinController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping
    public List<Pet> getDeletedPets() {
        return petService.getDeletedPets();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restorePet(@PathVariable Long id) {
        petService.restorePet(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hardDeletePet(@PathVariable Long id) {
        petService.hardDeletePet(id);
        return ResponseEntity.noContent().build();
    }
}
