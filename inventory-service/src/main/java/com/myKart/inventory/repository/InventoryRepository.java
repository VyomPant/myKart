package com.mykart.inventory.repository;

import com.mykart.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findBySkuCode(String skuCode);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT i FROM Inventory i WHERE i.skuCode IN :skuCodes")
    List<Inventory> findAllBySkuCodeInForUpdate(List<String> skuCodes);

    List<Inventory> findAllBySkuCodeIn(List<String> skuCodes);
}
