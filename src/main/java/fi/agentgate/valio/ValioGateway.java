package fi.agentgate.valio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Port to Valio Aimo's pricing and availability systems. Two adapters exist:
 * {@link MockValioGateway} (offline, deterministic) and {@link HttpValioGateway} (the challenge's mock API).
 */
public interface ValioGateway {

    /**
     * Price per SALES UNIT (not per kg). Use comparisonPrice/comparisonUnit to compare products of different size.
     *
     * @param unitPrice       excl. tax, per sales unit
     * @param unitPriceWithTax incl. tax, null if unknown
     * @param taxRate         percent, null if unknown
     * @param comparisonPrice price per comparisonUnit, null if unknown
     * @param comparisonUnit  "KG" or "L", null if unknown
     * @param validFrom       null if unknown
     * @param validTo         null = no known end
     */
    record PriceInfo(BigDecimal unitPrice, BigDecimal unitPriceWithTax, String currency, BigDecimal taxRate,
                     BigDecimal comparisonPrice, String comparisonUnit, LocalDate validFrom, LocalDate validTo) {
    }

    /**
     * @param deliverable     false if sales-blocked, or Valio returned no schedule line (= cannot be delivered)
     * @param salesBlocked    Valio says the product cannot be ordered at the moment
     * @param availableAmount in salesUnit; 0 = none. Not meaningful when amountKnown is false
     * @param amountKnown     false for Terminal products (Valio sends 999999 = quantity unknown)
     * @param availableDate   earliest date the amount is available; may be later than requested; null if none
     * @param closingTime     order cut-off, raw string from Valio
     * @param logisticModel   "Stock" or "Terminal" as returned by Valio
     */
    record AvailabilityInfo(boolean deliverable, boolean salesBlocked, BigDecimal availableAmount,
                            boolean amountKnown, LocalDate availableDate, String closingTime,
                            String logisticModel, String salesUnit) {
    }

    /** @return the price for the requested delivery date, or null if Valio has no price for this product */
    PriceInfo getPrice(String productCode, LocalDate requestedDate);

    /**
     * The quantity is not sent to Valio (its API answers per product and date); callers compare it with
     * availableAmount themselves.
     *
     * @return availability for the date, or null if Valio could not say
     */
    AvailabilityInfo getAvailability(String productCode, BigDecimal quantity, LocalDate requestedDate);
}
