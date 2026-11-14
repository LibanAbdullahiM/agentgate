package fi.agentgate.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchase_order")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    private UUID id;

    @Column(name = "quote_id", nullable = false, unique = true)
    private UUID quoteId;

    @Column(name = "agent_user_id", nullable = false)
    private UUID agentUserId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "decision_reason")
    private String decisionReason;

    /** Comma separated machine-readable reason codes from the guardrail engine (e.g. ORDER_ABOVE_LIMIT). */
    @Column(name = "decision_codes")
    private String decisionCodes;

    @Column(name = "decided_by")
    private UUID decidedBy;

    /** The human approver's or rejecter's comment. */
    @Column(name = "decided_comment")
    private String decidedComment;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
