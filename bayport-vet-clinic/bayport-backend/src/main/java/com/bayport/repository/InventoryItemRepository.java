package com.bayport.repository;

import com.bayport.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    Optional<InventoryItem> findBySku(String sku);

    Optional<InventoryItem> findFirstByNameIgnoreCase(String name);
}

