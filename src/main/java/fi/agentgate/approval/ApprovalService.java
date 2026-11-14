package fi.agentgate.approval;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.agentgate.audit.AuditService;
import fi.agentgate.common.ApiException;
import fi.agentgate.order.OrderRepository;
import fi.agentgate.order.OrderService;
import fi.agentgate.order.OrderStatus;
import fi.agentgate.order.PurchaseOrder;
import fi.agentgate.quote.Quote;
import fi.agentgate.quote.QuoteRepository;
import fi.agentgate.quote.QuoteSigner;
import fi.agentgate.security.AuthPrincipal;

/**
 * The human side of the guardrails: an approver decides orders the agent was not allowed to decide alone.
 * Every decision goes through {@link OrderService#transition}, so the state machine and the audit trail stay
 * the single source of truth.
 */
@Service
public class ApprovalService {

    private final OrderRepository orders;
    private final OrderService orderService;
    private final QuoteRepository quotes;
    private final QuoteSigner signer;
    private final AuditService audit;
    private final long ttlHours;

    public ApprovalService(OrderRepository orders, OrderService orderService, QuoteRepository quotes,
                           QuoteSigner signer, AuditService audit,
                           @Value("${app.approval.ttl-hours:24}") long ttlHours) {
        this.orders = orders;
        this.orderService = orderService;
        this.quotes = quotes;
        this.signer = signer;
        this.audit = audit;
        this.ttlHours = ttlHours;
    }

    /** Until when a human can still decide this order. After that it expires. */
    public Instant decideBy(PurchaseOrder order) {
        return order.getCreatedAt().plus(ttlHours, ChronoUnit.HOURS);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> pending() {
        return orders.findByStatusOrderByCreatedAtDesc(OrderStatus.PENDING_APPROVAL);
    }

    @Transactional
    public PurchaseOrder approve(AuthPrincipal approver, UUID orderId, String comment) {
        PurchaseOrder order = lockPending(orderId);
        Instant now = Instant.now();
        if (decideBy(order).isBefore(now)) {
            throw new ApiException(HttpStatus.GONE, "APPROVAL_EXPIRED",
                    "This order waited longer than " + ttlHours + " hours and is expiring. Ask the agent to order again.");
        }
        Quote quote = quotes.findById(order.getQuoteId()).orElseThrow();
        if (!signer.verify(quote)) {
            audit.recordIndependent(approver, "ORDER_BLOCKED", "ORDER", order.getId().toString(), "DENY",
                    "Quote signature did not verify at approval time: the terms were changed after issue.", null);
            throw ApiException.conflict("QUOTE_SIGNATURE_INVALID",
                    "The quote's signature is invalid, so the order cannot be approved");
        }
        String cleaned = clean(comment);
        order.setDecidedComment(cleaned);
        order.setDecidedAt(now);
        // The mock wholesaler has no order endpoint, so confirming is an internal step right after approval.
        orderService.transition(order, OrderStatus.APPROVED, approver, cleaned == null ? "No comment given." : cleaned);
        orderService.transition(order, OrderStatus.CONFIRMED, approver, "Confirmed after human approval");
        return order;
    }

    @Transactional
    public PurchaseOrder reject(AuthPrincipal approver, UUID orderId, String comment) {
        PurchaseOrder order = lockPending(orderId);
        String cleaned = clean(comment);
        order.setDecidedComment(cleaned);
        order.setDecidedAt(Instant.now());
        orderService.transition(order, OrderStatus.REJECTED, approver, cleaned == null ? "No comment given." : cleaned);
        return order;
    }

    /** Called by the expiry job, one order per transaction. Re-checks under the lock, so a human always wins a race. */
    @Transactional
    public void expireIfStale(UUID orderId) {
        orders.lockById(orderId)
                .filter(o -> o.getStatus() == OrderStatus.PENDING_APPROVAL && decideBy(o).isBefore(Instant.now()))
                .ifPresent(o -> orderService.transition(o, OrderStatus.EXPIRED, null,
                        "Nobody approved or rejected this order within " + ttlHours + " hours."));
    }

    private PurchaseOrder lockPending(UUID orderId) {
        PurchaseOrder order = orders.lockById(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No order with id " + orderId));
        if (order.getStatus() != OrderStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("NOT_PENDING_APPROVAL",
                    "This order is " + order.getStatus() + ", so it is not waiting for approval");
        }
        return order;
    }

    private static String clean(String comment) {
        return comment == null || comment.isBlank() ? null : comment.trim();
    }
}
