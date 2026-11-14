package fi.agentgate.policy;

import java.util.List;

/** Result of checking an order against an agent's mandate. */
public record Decision(Outcome outcome, List<String> reasonCodes, String explanation) {

    public enum Outcome {
        /** Within the mandate: the agent may commit on its own. */
        AUTO_APPROVE,
        /** A human must sign off. */
        NEEDS_HUMAN,
        /** Not allowed at all. */
        DENY
    }
}
