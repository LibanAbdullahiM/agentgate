package fi.agentgate.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import fi.agentgate.audit.AuditService;
import fi.agentgate.catalog.Product;
import fi.agentgate.catalog.ProductRepository;
import fi.agentgate.common.ApiException;
import fi.agentgate.security.AuthPrincipal;
import fi.agentgate.valio.ValioGateway;
import fi.agentgate.valio.ValioGateway.AvailabilityInfo;
import fi.agentgate.valio.ValioGateway.PriceInfo;

@Service
public class QuoteService {

    private final ProductRepository products;
    private final QuoteRepository quotes;
    private final ValioGateway valio;
    private final QuoteSigner signer;
    private final AuditService audit;
    private final long ttlMinutes;

    public QuoteService(ProductRepository products, QuoteRepository quotes, ValioGateway valio,
                        QuoteSigner signer, AuditService audit,
                        @Value("${app.quote.ttl-minutes}") long ttlMinutes) {
        this.products = products;
        this.quotes = quotes;
        this.valio = valio;
        this.signer = signer;
        this.audit = audit;
        this.ttlMinutes = ttlMinutes;
    }

    // Deliberately not @Transactional: we do not hold a DB connection while waiting for Valio's API.
    public Quote create(AuthPrincipal agent, String productCode, BigDecimal quantity, LocalDate neededBy) {
        Product product = products.findById(productCode)
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "No product with code " + productCode));
        if (neededBy.isBefore(LocalDate.now())) {
            throw ApiException.badRequest("DATE_IN_PAST", "neededBy must not be in the past");
        }

        PriceInfo price = valio.getPrice(product.getCode(), neededBy);
        if (price == null || price.unitPrice() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICE_UNAVAILABLE",
                    "No price is available for " + product.getCode() + ", so no binding quote can be issued");
        }
        AvailabilityInfo availability = valio.getAvailability(product.getCode(), quantity, neededBy);
        if (availability == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AVAILABILITY_UNKNOWN",
                    "Availability for " + product.getCode() + " could not be determined");
        }
        if (availability.salesBlocked()) {
            throw ApiException.conflict("PRODUCT_NOT_ORDERABLE",
                    "Valio has blocked sales of " + product.getCode() + " at the moment, so no quote can be issued");
        }

        // Round to the database scale BEFORE signing, so the signature still verifies after a DB round trip.
        BigDecimal qty = quantity.setScale(3, RoundingMode.HALF_UP);
        BigDecimal unitPrice = price.unitPrice().setScale(4, RoundingMode.HALF_UP);

        // Terminal products ship from the supplier's warehouse: Valio does not know the quantity (it sends 999999).
        // We never present that as real stock: it is assumed deliverable, and the trace says it is unconfirmed.
        BigDecimal available;
        if (!availability.deliverable()) {
            available = BigDecimal.ZERO;
        } else if (availability.amountKnown()) {
            available = availability.availableAmount().max(BigDecimal.ZERO);
        } else {
            available = qty;
        }
        available = available.setScale(3, RoundingMode.HALF_UP);

        // Valio answers with the EARLIEST date the amount is available, which may be after the requested date.
        boolean onTime = availability.availableDate() == null || !availability.availableDate().isAfter(neededBy);
        boolean fullyAvailable = availability.deliverable() && onTime && available.compareTo(qty) >= 0;

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        Quote q = new Quote();
        q.setId(UUID.randomUUID());
        q.setAgentUserId(agent.id());
        q.setProductCode(product.getCode());
        q.setQuantity(qty);
        q.setUnitPrice(unitPrice);
        q.setCurrency(price.currency() == null ? "EUR" : price.currency());
        q.setLineTotal(unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP));
        q.setNeededBy(neededBy);
        q.setAvailableQty(available.min(qty));
        q.setFullyAvailable(fullyAvailable);
        q.setEarliestDelivery(availability.availableDate());
        q.setCreatedAt(now);
        q.setExpiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES));
        q.setSignature(signer.sign(q));
        quotes.save(q);

        String stockNote = availability.deliverable() && !availability.amountKnown()
                ? "; stock NOT confirmed (terminal product: supplier delivers, quantity unknown to Valio)"
                : "";
        audit.record(agent, "QUOTE_ISSUED", "QUOTE", q.getId().toString(),
                fullyAvailable ? "FULLY_AVAILABLE" : "PARTIALLY_AVAILABLE",
                "Quoted " + quantity.stripTrailingZeros().toPlainString() + " x " + product.getCode()
                        + " at " + q.getUnitPrice().stripTrailingZeros().toPlainString() + " " + q.getCurrency(),
                "expiresAt=" + q.getExpiresAt() + stockNote);
        return q;
    }
}
