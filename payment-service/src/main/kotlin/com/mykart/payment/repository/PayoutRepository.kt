package com.mykart.payment.repository

import com.mykart.payment.entity.Payout
import com.mykart.payment.enums.PayoutStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface PayoutRepository : JpaRepository<Payout, UUID> {

    fun findByOrderId(orderId: String): Optional<Payout>

    fun countByStatus(status: PayoutStatus): Long

    @Query("""
        SELECT p FROM Payout p
        WHERE p.status = 'PENDING'
        OR (p.status = 'FAILED'
            AND p.failureType = 'TRANSIENT'
            AND p.retryCount < p.maxRetries
            AND (p.lastAttemptAt IS NULL OR p.lastAttemptAt < :threshold))
        ORDER BY p.createdAt ASC
    """)
    fun findPendingOrRetryable(@Param("threshold") threshold: Instant, pageable: Pageable): List<Payout>

    @Query("SELECT p FROM Payout p WHERE p.status = 'SUCCESS' AND p.externalReferenceId IS NOT NULL")
    fun findAllSuccessfulWithExternalRef(): List<Payout>

    @Query("""
        SELECT COUNT(p) FROM Payout p
        WHERE p.status = 'FAILED'
        AND p.failureType = 'TRANSIENT'
        AND p.retryCount < p.maxRetries
    """)
    fun countRetryable(): Long
}
