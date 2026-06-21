package com.mykart.order.repository;

import com.mykart.order.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {}
