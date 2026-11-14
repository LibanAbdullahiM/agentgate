package fi.agentgate.order;

public enum OrderStatus {
    /** Waiting for a human to approve or reject. */
    PENDING_APPROVAL,
    /** Approved (by the mandate or by a human), not yet confirmed to the wholesaler. */
    APPROVED,
    /** Committed. Final. */
    CONFIRMED,
    /** Refused by policy or by a human. Final. */
    REJECTED,
    /** Nobody decided in time. Final. */
    EXPIRED;

    public boolean canTransitionTo(OrderStatus to) {
        return switch (this) {
            case PENDING_APPROVAL -> to == APPROVED || to == REJECTED || to == EXPIRED;
            case APPROVED -> to == CONFIRMED;
            default -> false;
        };
    }

    public boolean isFinal() {
        return this == CONFIRMED || this == REJECTED || this == EXPIRED;
    }
}
