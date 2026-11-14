package fi.agentgate.approval;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import fi.agentgate.order.OrderRepository;
import fi.agentgate.order.OrderStatus;
import fi.agentgate.order.PurchaseOrder;

/** Moves orders nobody decided in time to EXPIRED (state machine: PENDING_APPROVAL -> EXPIRED). */
@Component
public class ApprovalExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ApprovalExpiryJob.class);

    private final OrderRepository orders;
    private final ApprovalService approvals;
    private final long ttlHours;

    public ApprovalExpiryJob(OrderRepository orders, ApprovalService approvals,
                             @Value("${app.approval.ttl-hours:24}") long ttlHours) {
        this.orders = orders;
        this.approvals = approvals;
        this.ttlHours = ttlHours;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void expireStaleOrders() {
        Instant cutoff = Instant.now().minus(ttlHours, ChronoUnit.HOURS);
        for (PurchaseOrder o : orders.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_APPROVAL, cutoff)) {
            try {
                approvals.expireIfStale(o.getId());
            } catch (RuntimeException e) {
                log.warn("Could not expire order {}: {}", o.getId(), e.getMessage());
            }
        }
    }
}
