package fi.agentgate.policy;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import fi.agentgate.catalog.Product;
import fi.agentgate.mandate.Mandate;
import fi.agentgate.quote.Quote;

/**
 * Deterministic guardrails. No AI here on purpose: a limit must behave the same way every time.
 *
 * <p>T6 version: mandate validity, allowed categories, spend limit. T7 adds substitution and allergen rules.
 */
@Service
public class PolicyService {

    public Decision evaluate(Mandate mandate, Quote quote, Product product, Instant now) {
        if (!mandate.isActive() || mandate.isExpired(now)) {
            return new Decision(Decision.Outcome.DENY, List.of("MANDATE_INACTIVE"),
                    "The agent's mandate is not active or has expired, so it cannot place orders.");
        }
        if (!mandate.allowsCategory(product.getCategory())) {
            return new Decision(Decision.Outcome.DENY, List.of("CATEGORY_NOT_ALLOWED"),
                    "Category '" + product.getCategory() + "' is outside this agent's mandate.");
        }
        if (quote.getLineTotal().compareTo(mandate.getMaxOrderValue()) > 0) {
            return new Decision(Decision.Outcome.NEEDS_HUMAN, List.of("ORDER_ABOVE_LIMIT"),
                    "Order total " + quote.getLineTotal() + " " + quote.getCurrency()
                            + " is above the agent's limit of " + mandate.getMaxOrderValue()
                            + " " + quote.getCurrency() + ", so a human must approve it.");
        }
        return new Decision(Decision.Outcome.AUTO_APPROVE, List.of("WITHIN_MANDATE"),
                "Order total " + quote.getLineTotal() + " " + quote.getCurrency()
                        + " is within the agent's limit of " + mandate.getMaxOrderValue()
                        + " " + quote.getCurrency() + ", so the agent may order autonomously.");
    }
}
