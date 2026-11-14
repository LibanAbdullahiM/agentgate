package fi.agentgate.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByAgentUserIdAndIdempotencyKey(UUID agentUserId, String idempotencyKey);

    boolean existsByQuoteId(UUID quoteId);

    List<PurchaseOrder> findByAgentUserIdOrderByCreatedAtDesc(UUID agentUserId);

    List<PurchaseOrder> findByAgentUserIdAndStatusOrderByCreatedAtDesc(UUID agentUserId, OrderStatus status);

    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();

    List<PurchaseOrder> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
