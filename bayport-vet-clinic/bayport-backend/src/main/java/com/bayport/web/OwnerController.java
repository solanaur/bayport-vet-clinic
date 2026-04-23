package com.bayport.web;

import com.bayport.entity.Owner;
import com.bayport.service.OwnerService;
import com.bayport.service.BayportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owners")
public class OwnerController {

    private final OwnerService ownerService;
    private final BayportService bayportService;

    public OwnerController(OwnerService ownerService, BayportService bayportService) {
        this.ownerService = ownerService;
        this.bayportService = bayportService;
    }

    @GetMapping
    public List<Owner> list() {
        return ownerService.list();
    }

    @GetMapping("/{id}")
    public Owner get(@PathVariable Long id) {
        return ownerService.get(id);
    }

    @GetMapping("/search")
    public List<Owner> search(@RequestParam(name = "q", required = false) String term) {
        return ownerService.search(term);
    }

    @PostMapping
    public Owner create(@Valid @RequestBody Owner owner) {
        return ownerService.create(owner);
    }

    @PutMapping("/{id}")
    public Owner update(@PathVariable Long id, @Valid @RequestBody Owner owner) {
        return ownerService.update(id, owner);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bayportService.deleteOwnerCascade(id);
        return ResponseEntity.noContent().build();
    }
}

