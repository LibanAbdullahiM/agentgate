package fi.agentgate.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByAgentUserIdAndIdempotencyKey(UUID agentUserId, String idempotencyKey);

    boolean existsByQuoteId(UUID quoteId);

    List<PurchaseOrder> findByAgentUserIdOrderByCreatedAtDesc(UUID agentUserId);

    List<PurchaseOrder> findByAgentUserIdAndStatusOrderByCreatedAtDesc(UUID agentUserId, OrderStatus status);

    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();

    List<PurchaseOrder> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<PurchaseOrder> findByStatusAndCreatedAtBefore(OrderStatus status, Instant cutoff);

    /** Row lock, so two approvers (or an approver and the expiry job) can never decide the same order twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> lockById(@Param("id") UUID id);
}
