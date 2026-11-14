package fi.agentgate.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import fi.agentgate.catalog.Product;
import fi.agentgate.mandate.Mandate;
import fi.agentgate.quote.Quote;

/**
 * Deterministic guardrails. No AI here on purpose: a limit must behave the same way every time.
 *
 * <p>Every rule is checked and ALL findings are reported, not just the first one, so the human (or the agent)
 * sees every reason at once. The worst finding decides: DENY beats NEEDS_HUMAN beats AUTO_APPROVE.
 *
 * <p>Rules: mandate valid, category allowed, allergens the buyer excludes (contains = deny; traces or unknown
 * = ask a human), stock not confirmed by Valio (terminal products), spend limit.
 */
@Service
public class PolicyService {

    private record Finding(Decision.Outcome outcome, String code, String text) {
    }

    public Decision evaluate(Mandate mandate, Quote quote, Product product, Instant now) {
        if (!mandate.isActive() || mandate.isExpired(now)) {
            return new Decision(Decision.Outcome.DENY, List.of("MANDATE_INACTIVE"),
                    "The agent's mandate is not active or has expired, so it cannot place orders.");
        }
        List<Finding> findings = new ArrayList<>();

        if (!mandate.allowsCategory(product.getCategory())) {
            findings.add(new Finding(Decision.Outcome.DENY, "CATEGORY_NOT_ALLOWED",
                    "Category '" + product.getCategory() + "' is outside this agent's mandate."));
        }

        for (String allergen : mandate.avoidAllergenList()) {
            switch (product.allergenStatus(allergen)) {
                case CONTAINS -> findings.add(new Finding(Decision.Outcome.DENY, "ALLERGEN_CONFLICT",
                        "The product contains allergen " + allergen + ", which this buyer excludes."));
                case MAY_CONTAIN -> findings.add(new Finding(Decision.Outcome.NEEDS_HUMAN, "ALLERGEN_TRACES",
                        "The product may contain traces of allergen " + allergen + ", which this buyer excludes."));
                case UNKNOWN -> findings.add(new Finding(Decision.Outcome.NEEDS_HUMAN, "ALLERGEN_UNKNOWN",
                        "Valio gives no information on allergen " + allergen
                                + " for this product, so it cannot be shown to be safe."));
                case FREE_FROM -> {
                    // explicitly free from this allergen: no finding
                }
            }
        }

        if (!quote.isStockConfirmed()) {
            findings.add(new Finding(Decision.Outcome.NEEDS_HUMAN, "STOCK_UNCONFIRMED",
                    "Valio does not know the stock of this product (it ships from the supplier's warehouse), "
                            + "so availability is not confirmed."));
        }

        if (quote.getLineTotal().compareTo(mandate.getMaxOrderValue()) > 0) {
            findings.add(new Finding(Decision.Outcome.NEEDS_HUMAN, "ORDER_ABOVE_LIMIT",
                    "Order total " + quote.getLineTotal() + " " + quote.getCurrency()
                            + " is above the agent's limit of " + mandate.getMaxOrderValue()
                            + " " + quote.getCurrency() + "."));
        }

        if (findings.isEmpty()) {
            return new Decision(Decision.Outcome.AUTO_APPROVE, List.of("WITHIN_MANDATE"),
                    "Order total " + quote.getLineTotal() + " " + quote.getCurrency()
                            + " is within the agent's limit of " + mandate.getMaxOrderValue()
                            + " " + quote.getCurrency() + ", and no other rule applies, so the agent may order "
                            + "autonomously.");
        }

        boolean deny = findings.stream().anyMatch(f -> f.outcome() == Decision.Outcome.DENY);
        String sentences = findings.stream().map(Finding::text).collect(Collectors.joining(" "));
        return new Decision(deny ? Decision.Outcome.DENY : Decision.Outcome.NEEDS_HUMAN,
                findings.stream().map(Finding::code).toList(),
                sentences + (deny ? " The order is refused." : " A human must approve it."));
    }
}
