package fi.agentgate.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import fi.agentgate.user.UserRepository;

/**
 * Turns raw audit events into something a non-technical approver can read: who did it (a name, not a UUID)
 * and one plain-language sentence. The sentence is built by fixed rules from the recorded facts, never by an LLM,
 * so it can be trusted in an audit.
 */
@Component
public class AuditNarrator {

    public record AuditView(Long id, Instant at, String actor, String actorRole, String action,
                            String entityType, String entityId, String decision, String reason,
                            String summary, String details) {
    }

    private final UserRepository users;

    public AuditNarrator(UserRepository users) {
        this.users = users;
    }

    public List<AuditView> narrate(List<AuditEvent> events) {
        Set<UUID> ids = events.stream().map(AuditEvent::getActorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            users.findAllById(ids).forEach(u -> names.put(u.getId(), u.getUsername()));
        }
        return events.stream().map(e -> view(e, names)).toList();
    }

    private AuditView view(AuditEvent e, Map<UUID, String> names) {
        String actor = e.getActorId() == null ? "system" : names.getOrDefault(e.getActorId(), "unknown user");
        return new AuditView(e.getId(), e.getOccurredAt(), actor, e.getActorRole(), e.getAction(),
                e.getEntityType(), e.getEntityId(), e.getDecision(), e.getReason(),
                summary(e, actor), e.getDetails());
    }

    static String summary(AuditEvent e, String actor) {
        String reason = e.getReason() == null ? "" : e.getReason().trim();
        String decision = e.getDecision() == null ? "" : e.getDecision();
        String comment = (reason.isEmpty() || reason.equals("No comment given."))
                ? "" : " Comment: " + end(reason);
        String text = switch (e.getAction()) {
            case "QUOTE_ISSUED" -> actor + " asked for a price and received a signed, time-limited quote: " + end(reason);
            case "ORDER_PLACED" -> actor + " placed an order. The guardrails decided " + decision + ": " + end(reason);
            case "ORDER_BLOCKED" -> "An order attempt by " + actor + " was blocked: " + end(reason);
            case "ORDER_APPROVED" -> actor + " approved the order." + comment;
            case "ORDER_REJECTED" -> actor + " rejected the order." + comment;
            case "ORDER_CONFIRMED" -> reason.isEmpty() ? "The order was confirmed." : end(reason);
            case "ORDER_EXPIRED" -> reason.isEmpty() ? "The order expired." : "The order expired: " + end(reason);
            case "MANDATE_UPDATED" -> actor + " changed a mandate. " + end(reason)
                    + (e.getDetails() == null ? "" : " Changes: " + end(e.getDetails()));
            default -> actor + " performed " + e.getAction() + ". " + end(reason);
        };
        return text.trim();
    }

    /** Makes sure a fragment ends like a sentence, so joined text reads cleanly. */
    private static String end(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String t = s.trim();
        return t.endsWith(".") || t.endsWith("!") || t.endsWith("?") ? t : t + ".";
    }
}
