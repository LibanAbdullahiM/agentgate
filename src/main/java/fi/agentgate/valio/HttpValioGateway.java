package fi.agentgate.valio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import fi.agentgate.common.ApiException;

/**
 * Adapter for Valio Aimo's challenge API (VALIO_MODE=live).
 *
 * <p>POST {base}/price and POST {base}/availability, body {customer, products[], requestedDate}.
 * Customer id does not matter in this challenge. The price response nesting below is read defensively because
 * only part of it was visible in the docs: check the first live response (debug log) and tighten if needed.
 */
@Component
@ConditionalOnProperty(name = "valio.mode", havingValue = "live")
public class HttpValioGateway implements ValioGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpValioGateway.class);
    private static final BigDecimal TERMINAL_PLACEHOLDER = new BigDecimal("999999");

    private final RestClient client;
    private final String pricingPath;
    private final String availabilityPath;
    private final String customerId;

    public HttpValioGateway(@Value("${valio.base-url}") String baseUrl,
                            @Value("${valio.api-key}") String apiKey,
                            @Value("${valio.pricing-path}") String pricingPath,
                            @Value("${valio.availability-path}") String availabilityPath,
                            @Value("${valio.customer-id:123}") String customerId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("VALIO_API_KEY is required when VALIO_MODE=live");
        }
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
        this.pricingPath = pricingPath;
        this.availabilityPath = availabilityPath;
        this.customerId = customerId;
    }

    @Override
    public PriceInfo getPrice(String productCode, LocalDate requestedDate) {
        JsonNode body = post(pricingPath, productCode, requestedDate);
        JsonNode p = findProduct(body, productCode);
        if (p == null) {
            return null;
        }
        JsonNode catalog = p.path("catalogPrice");
        BigDecimal net = dec(catalog.path("value"));
        if (net == null) {
            return null; // no price from Valio: the quote layer must refuse, never guess
        }
        BigDecimal withTax = dec(catalog.path("valueWithTax"));
        if (withTax == null) {
            withTax = amount(p.path("priceWithTax"));
        }
        String currency = text(body, "currency");
        LocalDate validFrom = date(catalog.path("validFrom"));
        if (validFrom == null) {
            validFrom = date(p.path("validFrom"));
        }
        LocalDate validTo = date(catalog.path("validTo"));
        if (validTo == null) {
            validTo = date(p.path("validTo"));
        }
        return new PriceInfo(net, withTax, currency == null ? "EUR" : currency,
                dec(p.path("tax").path("taxRate")),
                amount(p.path("comparisonPrice")), text(p, "comparisonUnit"),
                validFrom, validTo);
    }

    @Override
    public AvailabilityInfo getAvailability(String productCode, BigDecimal quantity, LocalDate requestedDate) {
        JsonNode body = post(availabilityPath, productCode, requestedDate);
        JsonNode p = findProduct(body, productCode);
        if (p == null) {
            return null;
        }
        String logistic = text(p, "logisticModel");
        boolean terminal = logistic != null && logistic.equalsIgnoreCase("terminal");
        boolean blocked = p.path("salesBlock").asBoolean(false);
        String closing = text(p, "closingTime");

        JsonNode lines = p.path("scheduleLines");
        if (!lines.isArray() || lines.isEmpty()) {
            // Valio: empty array = cannot be delivered on any date
            return new AvailabilityInfo(false, blocked, BigDecimal.ZERO, !terminal, null, closing, logistic, null);
        }
        JsonNode line = lines.get(0);
        blocked = blocked || line.path("salesBlock").asBoolean(false);
        BigDecimal amount = dec(line.path("availableAmount"));
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        boolean amountKnown = !terminal && amount.compareTo(TERMINAL_PLACEHOLDER) != 0;
        boolean deliverable = !blocked && amount.signum() > 0;
        return new AvailabilityInfo(deliverable, blocked, amount, amountKnown,
                date(line.path("availableDate")), closing, logistic, text(line, "salesUnit"));
    }

    private JsonNode post(String path, String productCode, LocalDate requestedDate) {
        Map<String, Object> request = Map.of(
                "customer", customerId,
                "products", List.of(productCode),
                "requestedDate", requestedDate.toString());
        try {
            JsonNode body = client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            log.debug("Valio {} {} -> {}", path, productCode, body);
            return body;
        } catch (RestClientException e) {
            log.warn("Valio API call failed for {} ({}): {}", path, productCode, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR",
                    "Valio Aimo's system could not answer this request right now");
        }
    }

    private static JsonNode findProduct(JsonNode body, String productCode) {
        if (body == null) {
            return null;
        }
        for (JsonNode p : body.path("products")) {
            if (productCode.equals(p.path("productId").asText())) {
                return p;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static BigDecimal dec(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        if (n.isTextual()) {
            try {
                return new BigDecimal(n.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** A number, or an object holding the number under "value". */
    private static BigDecimal amount(JsonNode n) {
        if (n != null && n.isObject()) {
            return dec(n.path("value"));
        }
        return dec(n);
    }

    private static LocalDate date(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        String s = n.asText();
        if (s == null || s.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
