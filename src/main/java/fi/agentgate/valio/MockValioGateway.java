package fi.agentgate.valio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic fake prices and stock derived from the product code, so the frontend and demos work
 * without network access. Same code always gives the same numbers. It mimics Valio's real behaviour:
 * the answer is the earliest date the amount is available (possibly later than requested), about 1 in 10
 * products is a Terminal product (quantity unknown) and about 2 in 100 are sales-blocked.
 */
@Component
@ConditionalOnProperty(name = "valio.mode", havingValue = "mock", matchIfMissing = true)
public class MockValioGateway implements ValioGateway {

    @Override
    public PriceInfo getPrice(String productCode, LocalDate requestedDate) {
        int h = hash(productCode);
        // 1.00 .. 24.99 EUR
        BigDecimal price = BigDecimal.valueOf(100 + (h % 2400), 2);
        BigDecimal withTax = price.multiply(new BigDecimal("1.14")).setScale(2, RoundingMode.HALF_UP);
        return new PriceInfo(price, withTax, "EUR", new BigDecimal("14"), null, null, requestedDate, null);
    }

    @Override
    public AvailabilityInfo getAvailability(String productCode, BigDecimal quantity, LocalDate requestedDate) {
        int h = hash(productCode);
        boolean blocked = h % 50 == 7;
        boolean terminal = h % 10 == 0;
        LocalDate earliest = LocalDate.now().plusDays(1);
        LocalDate availableDate = requestedDate.isBefore(earliest) ? earliest : requestedDate;
        BigDecimal stock = BigDecimal.valueOf(200 + (h % 4800));
        BigDecimal amount = blocked ? BigDecimal.ZERO : terminal ? new BigDecimal("999999") : stock;
        return new AvailabilityInfo(!blocked, blocked, amount, !terminal, availableDate, "12:00",
                terminal ? "Terminal" : "Stock", "pcs");
    }

    private static int hash(String code) {
        return Math.abs(code.hashCode() % 100_000);
    }
}
