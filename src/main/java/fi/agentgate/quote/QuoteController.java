package fi.agentgate.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fi.agentgate.catalog.Product;
import fi.agentgate.catalog.ProductRepository;
import fi.agentgate.common.ApiException;
import fi.agentgate.security.AuthPrincipal;

@RestController
@RequestMapping("/api/v1/quotes")
@Tag(name = "Quotes")
@SecurityRequirement(name = "bearerAuth")
public class QuoteController {

    public record QuoteRequest(@NotBlank String productCode,
                               @NotNull @DecimalMin(value = "0.001", message = "must be greater than 0") BigDecimal quantity,
                               @NotNull LocalDate neededBy) {
    }

    public record QuoteView(UUID id, String productCode, String productName, String unit,
                            BigDecimal quantity, BigDecimal unitPrice, String currency, BigDecimal lineTotal,
                            LocalDate neededBy, BigDecimal availableQuantity, boolean fullyAvailable,
                            LocalDate earliestDeliveryDate, Instant issuedAt, Instant expiresAt,
                            String signature, String signatureAlgorithm, String signedPayload) {
    }

    private final QuoteService service;
    private final QuoteRepository quotes;
    private final ProductRepository products;
    private final QuoteSigner signer;

    public QuoteController(QuoteService service, QuoteRepository quotes, ProductRepository products,
                           QuoteSigner signer) {
        this.service = service;
        this.quotes = quotes;
        this.products = products;
        this.signer = signer;
    }

    /** Ask for a fixed price + availability for one product, quantity and delivery date. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('AGENT')")
    public QuoteView create(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody QuoteRequest req) {
        return view(service.create(principal, req.productCode().trim(), req.quantity(), req.neededBy()));
    }

    @GetMapping("/{id}")
    public QuoteView get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        Quote q = quotes.findById(id)
                .filter(x -> !principal.isAgent() || x.getAgentUserId().equals(principal.id()))
                .orElseThrow(() -> ApiException.notFound("QUOTE_NOT_FOUND", "No quote with id " + id));
        return view(q);
    }

    private QuoteView view(Quote q) {
        Product p = products.findById(q.getProductCode()).orElse(null);
        return new QuoteView(q.getId(), q.getProductCode(), p == null ? null : p.getName(),
                p == null ? null : p.getUnit(), q.getQuantity(), q.getUnitPrice(), q.getCurrency(),
                q.getLineTotal(), q.getNeededBy(), q.getAvailableQty(), q.isFullyAvailable(),
                q.getEarliestDelivery(), q.getCreatedAt(), q.getExpiresAt(), q.getSignature(),
                QuoteSigner.ALGORITHM, signer.canonical(q));
    }
}
