package fi.agentgate.approval;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.agentgate.catalog.Product;
import fi.agentgate.catalog.ProductRepository;
import fi.agentgate.order.OrderStatus;
import fi.agentgate.order.PurchaseOrder;
import fi.agentgate.quote.Quote;
import fi.agentgate.quote.QuoteRepository;
import fi.agentgate.security.AuthPrincipal;
import fi.agentgate.user.AppUser;
import fi.agentgate.user.UserRepository;

/** The approval inbox: what the human approver sees and decides. Agents cannot call any of this. */
@RestController
@RequestMapping("/api/v1/approvals")
@Tag(name = "Approvals")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('APPROVER','BUYER_ADMIN')")
public class ApprovalController {

    public record PendingView(UUID orderId, UUID quoteId, String agent, String productCode, String productName,
                              String unit, BigDecimal quantity, BigDecimal unitPrice, String currency,
                              BigDecimal total, LocalDate neededBy, String reason, List<String> reasonCodes,
                              Instant requestedAt, Instant decideBy) {
    }

    public record ApproveRequest(@Size(max = 1000) String comment) {
    }

    public record RejectRequest(@NotBlank(message = "a reason is required when rejecting") @Size(max = 1000) String comment) {
    }

    public record DecisionView(UUID orderId, OrderStatus status, String decidedBy, String comment, Instant decidedAt) {
    }

    private final ApprovalService approvals;
    private final QuoteRepository quotes;
    private final ProductRepository products;
    private final UserRepository users;

    public ApprovalController(ApprovalService approvals, QuoteRepository quotes, ProductRepository products,
                              UserRepository users) {
        this.approvals = approvals;
        this.quotes = quotes;
        this.products = products;
        this.users = users;
    }

    /** Orders waiting for a human, newest first, with the reasons the guardrails escalated them. */
    @GetMapping
    public List<PendingView> pending() {
        return approvals.pending().stream().map(this::view).toList();
    }

    /** Approving confirms the order. The comment is optional and goes into the audit trail. */
    @PostMapping("/{orderId}/approve")
    public DecisionView approve(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID orderId,
                                @Valid @RequestBody(required = false) ApproveRequest req) {
        PurchaseOrder o = approvals.approve(principal, orderId, req == null ? null : req.comment());
        return decision(principal, o);
    }

    /** Rejecting needs a reason, so the agent and the audit trail can say why. */
    @PostMapping("/{orderId}/reject")
    public DecisionView reject(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID orderId,
                               @Valid @RequestBody RejectRequest req) {
        PurchaseOrder o = approvals.reject(principal, orderId, req.comment());
        return decision(principal, o);
    }

    private DecisionView decision(AuthPrincipal principal, PurchaseOrder o) {
        return new DecisionView(o.getId(), o.getStatus(), principal.username(), o.getDecidedComment(),
                o.getDecidedAt());
    }

    private PendingView view(PurchaseOrder o) {
        Quote q = quotes.findById(o.getQuoteId()).orElseThrow();
        Product p = products.findById(q.getProductCode()).orElse(null);
        String agent = users.findById(o.getAgentUserId()).map(AppUser::getUsername).orElse("unknown");
        List<String> codes = (o.getDecisionCodes() == null || o.getDecisionCodes().isBlank())
                ? List.of() : List.of(o.getDecisionCodes().split(","));
        return new PendingView(o.getId(), o.getQuoteId(), agent, q.getProductCode(),
                p == null ? null : p.getName(), p == null ? null : p.getUnit(), q.getQuantity(), q.getUnitPrice(),
                q.getCurrency(), o.getTotal(), q.getNeededBy(), o.getDecisionReason(), codes,
                o.getCreatedAt(), approvals.decideBy(o));
    }
}
