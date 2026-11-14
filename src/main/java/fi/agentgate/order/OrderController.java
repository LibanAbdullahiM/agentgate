package fi.agentgate.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fi.agentgate.audit.AuditEvent;
import fi.agentgate.audit.AuditNarrator;
import fi.agentgate.audit.AuditService;
import fi.agentgate.catalog.Product;
import fi.agentgate.catalog.ProductRepository;
import fi.agentgate.quote.Quote;
import fi.agentgate.quote.QuoteRepository;
import fi.agentgate.security.AuthPrincipal;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    public record PlaceOrderRequest(@NotNull UUID quoteId) {
    }

    public record OrderView(UUID id, UUID quoteId, OrderStatus status, String productCode, String productName,
                            BigDecimal quantity, BigDecimal unitPrice, String currency, BigDecimal total,
                            LocalDate neededBy, String decisionReason, Instant createdAt, Instant updatedAt,
                            List<String> decisionCodes, String decidedComment, Instant decidedAt) {
    }

    private final OrderService service;
    private final QuoteRepository quotes;
    private final ProductRepository products;
    private final AuditService audit;
    private final AuditNarrator narrator;

    public OrderController(OrderService service, QuoteRepository quotes, ProductRepository products,
                           AuditService audit, AuditNarrator narrator) {
        this.service = service;
        this.quotes = quotes;
        this.products = products;
        this.audit = audit;
        this.narrator = narrator;
    }

    /**
     * Place an order from a quote. Requires an Idempotency-Key header so retries never create duplicates.
     * 201 = new order, 200 = replay of an earlier request with the same key.
     */
    @PostMapping
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<OrderView> place(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Parameter(description = "Unique per order attempt, e.g. a UUID")
                                           @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
                                           @Valid @RequestBody PlaceOrderRequest req) {
        OrderService.PlaceResult result = service.place(principal, req.quoteId(), key.trim());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(view(result.order()));
    }

    /** Agents see their own orders; approvers and admins see all. Optional filter: ?status=PENDING_APPROVAL */
    @GetMapping
    public List<OrderView> list(@AuthenticationPrincipal AuthPrincipal principal,
                                @RequestParam(required = false) OrderStatus status) {
        return service.list(principal, status).stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public OrderView get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return view(service.getVisible(principal, id));
    }

    /**
     * Everything that happened to this order and its quote, oldest first. Each entry has the raw facts and a
     * plain-language {@code summary} that a non-technical approver can read.
     */
    @GetMapping("/{id}/trace")
    public List<AuditNarrator.AuditView> trace(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID id) {
        PurchaseOrder order = service.getVisible(principal, id);
        List<AuditEvent> events = audit.traceAll(List.of(
                new String[]{"QUOTE", order.getQuoteId().toString()},
                new String[]{"ORDER", order.getId().toString()}));
        return narrator.narrate(events);
    }

    private OrderView view(PurchaseOrder o) {
        Quote q = quotes.findById(o.getQuoteId()).orElseThrow();
        Product p = products.findById(q.getProductCode()).orElse(null);
        List<String> codes = (o.getDecisionCodes() == null || o.getDecisionCodes().isBlank())
                ? List.of() : List.of(o.getDecisionCodes().split(","));
        return new OrderView(o.getId(), o.getQuoteId(), o.getStatus(), q.getProductCode(),
                p == null ? null : p.getName(), q.getQuantity(), q.getUnitPrice(), q.getCurrency(),
                o.getTotal(), q.getNeededBy(), o.getDecisionReason(), o.getCreatedAt(), o.getUpdatedAt(),
                codes, o.getDecidedComment(), o.getDecidedAt());
    }
}
