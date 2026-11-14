package fi.agentgate.user;

public enum Role {
    /** Buyer-side administrator: manages mandates, sees everything. */
    BUYER_ADMIN,
    /** Human who signs off orders the agent may not decide alone. */
    APPROVER,
    /** The autonomous purchasing agent. */
    AGENT
}
