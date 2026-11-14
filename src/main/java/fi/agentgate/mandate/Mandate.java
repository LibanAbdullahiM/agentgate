package fi.agentgate.mandate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What one purchasing agent may do on behalf of one buyer. */
@Entity
@Table(name = "mandate")
@Getter
@Setter
@NoArgsConstructor
public class Mandate {

    @Id
    private UUID id;

    @Column(name = "agent_user_id", nullable = false)
    private UUID agentUserId;

    @Column(name = "buyer_name", nullable = false)
    private String buyerName;

    @Column(name = "max_order_value", nullable = false)
    private BigDecimal maxOrderValue;

    @Column(name = "allow_substitutions", nullable = false)
    private boolean allowSubstitutions;

    /** Comma separated category codes; null or blank means all categories. */
    @Column(name = "allowed_categories")
    private String allowedCategories;

    /** Comma separated GS1 allergen codes the buyer excludes: products must be FREE_FROM all of them. */
    @Column(name = "avoid_allergens")
    private String avoidAllergens;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean allowsCategory(String category) {
        if (allowedCategories == null || allowedCategories.isBlank()) {
            return true;
        }
        if (category == null) {
            return false;
        }
        return categoryList().stream().anyMatch(c -> c.equalsIgnoreCase(category.trim()));
    }

    public List<String> categoryList() {
        return split(allowedCategories);
    }

    public List<String> avoidAllergenList() {
        return split(avoidAllergens);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
