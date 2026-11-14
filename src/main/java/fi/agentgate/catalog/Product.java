package fi.agentgate.catalog;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    /** What Valio's data says about one allergen code for this product. UNKNOWN is never "safe". */
    public enum AllergenStatus {
        CONTAINS, MAY_CONTAIN, FREE_FROM, UNKNOWN
    }

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(nullable = false)
    private String unit = "pcs";

    /** Comma separated GS1 allergen codes the product CONTAINS. Only meaningful when allergensKnown is true. */
    private String allergens;

    /** Comma separated codes the product MAY CONTAIN (traces). */
    @Column(name = "allergens_may_contain")
    private String allergensMayContain;

    /** Comma separated codes Valio states the product is FREE FROM. */
    @Column(name = "allergens_free_from")
    private String allergensFreeFrom;

    /**
     * True when Valio gave any allergen data for this product. False means "we have no allergen data": it must never
     * be read as "allergen free". Even when true, a code that is not listed anywhere is still UNKNOWN.
     */
    @Column(name = "allergens_known", nullable = false)
    private boolean allergensKnown;

    private String supplier;

    /** Valio's allowedLotSize: orders must be a multiple of this, in sales units. Null = unknown. */
    @Column(name = "lot_size")
    private BigDecimal lotSize;

    @Column(nullable = false)
    private boolean active = true;

    /** Codes the product contains. */
    public List<String> allergenList() {
        return allergensKnown ? split(allergens) : List.of();
    }

    public List<String> mayContainList() {
        return allergensKnown ? split(allergensMayContain) : List.of();
    }

    public List<String> freeFromList() {
        return allergensKnown ? split(allergensFreeFrom) : List.of();
    }

    /** The deterministic answer used by allergen-safe mode: only FREE_FROM counts as safe. */
    public AllergenStatus allergenStatus(String allergenCode) {
        if (!allergensKnown || allergenCode == null) {
            return AllergenStatus.UNKNOWN;
        }
        String c = allergenCode.trim();
        if (split(allergens).contains(c)) {
            return AllergenStatus.CONTAINS;
        }
        if (split(allergensMayContain).contains(c)) {
            return AllergenStatus.MAY_CONTAIN;
        }
        if (split(allergensFreeFrom).contains(c)) {
            return AllergenStatus.FREE_FROM;
        }
        return AllergenStatus.UNKNOWN;
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
