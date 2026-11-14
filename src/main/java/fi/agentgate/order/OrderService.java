package fi.agentgate.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.agentgate.audit.AuditService;
import fi.agentgate.catalog.Product;
import fi.agentgate.catalog.ProductRepository;
import fi.agentgate.common.ApiException;
import fi.agentgate.mandate.Mandate;
import fi.agentgate.mandate.MandateRepository;
import fi.agentgate.policy.Decision;
import fi.agentgate.policy.PolicyService;
import fi.agentgate.quote.Quote;
import fi.agentgate.quote.QuoteRepository;
import fi.agentgate.quote.QuoteSigner;
import fi.agentgate.security.AuthPrincipal;

@Service
public class OrderService {

    public record PlaceResult(PurchaseOrder order, boolean created) {
    }

    private final OrderRepository orders;
    private final QuoteRepository quotes;
    private final QuoteSigner signer;
    private final MandateRepository mandates;
    private final ProductRepository products;
    private final PolicyService policy;
    private final AuditService audit;

    public OrderService(OrderRepository orders, QuoteRepository quotes, QuoteSigner signer,
                        MandateRepository mandates, ProductRepository products,
                        PolicyService policy, AuditService audit) {
        this.orders = orders;
        this.quotes = quotes;
        this.signer = signer;
        this.mandates = mandates;
        this.products = products;
        this.policy = policy;
        this.audit = audit;
    }

    /**
     * Turns a signed quote into an order. Safe to retry with the same Idempotency-Key: the original order is
     * returned and nothing is created twice.
     */
    @Transactional
    public PlaceResult place(AuthPrincipal agent, UUID quoteId, String idempotencyKey) {
        var replay = orders.findByAgentUserIdAndIdempotencyKey(agent.id(), idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().getQuoteId().equals(quoteId)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used for a different quote");
            }
            return new PlaceResult(replay.get(), false);
        }

        Quote quote = quotes.findById(quoteId)
                .filter(q -> q.getAgentUserId().equals(agent.id()))
                .orElseThrow(() -> ApiException.notFound("QUOTE_NOT_FOUND", "No quote with id " + quoteId));

        if (!signer.verify(quote)) {
            audit.recordIndependent(agent, "ORDER_BLOCKED", "QUOTE", quoteId.toString(), "DENY",
                    "Quote signature did not verify: the terms were changed after issue.", null);
            throw ApiException.conflict("QUOTE_SIGNATURE_INVALID",
                    "The quote's signature is invalid, so it cannot be ordered");
        }
        Instant now = Instant.now();
        if (quote.getExpiresAt().isBefore(now)) {
            throw new ApiException(HttpStatus.GONE, "QUOTE_EXPIRED",
                    "This quote expired at " + quote.getExpiresAt() + ". Request a new quote.");
        }
        if (!quote.isFullyAvailable()) {
            throw ApiException.conflict("QUOTE_NOT_FULLY_AVAILABLE",
                    "Only " + quote.getAvailableQty().stripTrailingZeros().toPlainString()
                            + " of the requested quantity is available for " + quote.getNeededBy()
                            + ". Request a smaller quantity or a later date.");
        }
        if (orders.existsByQuoteId(quoteId)) {
            throw ApiException.conflict("QUOTE_ALREADY_ORDERED", "This quote has already been ordered");
        }

        Mandate mandate = mandates.findFirstByAgentUserIdAndActiveTrue(agent.id())
                .orElseThrow(() -> ApiException.forbidden("NO_ACTIVE_MANDATE",
                        "This agent has no active mandate, so it cannot place orders"));
        Product product = products.findById(quote.getProductCode())
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product no longer exists"));

        Decision decision = policy.evaluate(mandate, quote, product, now);

        PurchaseOrder order = new PurchaseOrder();
        order.setId(UUID.randomUUID());
        order.setQuoteId(quoteId);
        order.setAgentUserId(agent.id());
        order.setIdempotencyKey(idempotencyKey);
        order.setTotal(quote.getLineTotal());
        order.setDecisionReason(decision.explanation());
        order.setDecisionCodes(String.join(",", decision.reasonCodes()));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setStatus(switch (decision.outcome()) {
            case AUTO_APPROVE -> OrderStatus.APPROVED;
            case NEEDS_HUMAN -> OrderStatus.PENDING_APPROVAL;
            case DENY -> OrderStatus.REJECTED;
        });
        orders.save(order);

        audit.record(agent, "ORDER_PLACED", "ORDER", order.getId().toString(), decision.outcome().name(),
                decision.explanation(), "reasonCodes=" + String.join(",", decision.reasonCodes()));

        if (decision.outcome() == Decision.Outcome.AUTO_APPROVE) {
            // The mock wholesaler has no order endpoint, so confirming is an internal step.
            transition(order, OrderStatus.CONFIRMED, null, "Confirmed automatically within the agent's mandate");
        }
        return new PlaceResult(order, true);
    }

    /** The only place that changes an order's status. Illegal moves are refused. */
    @Transactional
    public void transition(PurchaseOrder order, OrderStatus to, AuthPrincipal actor, String reason) {
        OrderStatus from = order.getStatus();
        if (!from.canTransitionTo(to)) {
            throw ApiException.conflict("ILLEGAL_TRANSITION",
                    "An order in status " + from + " cannot move to " + to);
        }
        order.setStatus(to);
        order.setUpdatedAt(Instant.now());
        if (actor != null) {
            order.setDecidedBy(actor.id());
        }
        orders.save(order);
        audit.record(actor, "ORDER_" + to.name(), "ORDER", order.getId().toString(), to.name(), reason,
                "from=" + from);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getVisible(AuthPrincipal caller, UUID id) {
        return orders.findById(id)
                .filter(o -> !caller.isAgent() || o.getAgentUserId().equals(caller.id()))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No order with id " + id));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> list(AuthPrincipal caller, OrderStatus status) {
        if (caller.isAgent()) {
            return status == null
                    ? orders.findByAgentUserIdOrderByCreatedAtDesc(caller.id())
                    : orders.findByAgentUserIdAndStatusOrderByCreatedAtDesc(caller.id(), status);
        }
        return status == null
                ? orders.findAllByOrderByCreatedAtDesc()
                : orders.findByStatusOrderByCreatedAtDesc(status);
    }
}
