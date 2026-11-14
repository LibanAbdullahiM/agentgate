package fi.agentgate.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A fixed-price offer valid until {@link #expiresAt}, signed so any tampering is detectable. */
@Entity
@Table(name = "quote")
@Getter
@Setter
@NoArgsConstructor
public class Quote {

    @Id
    private UUID id;

    @Column(name = "agent_user_id", nullable = false)
    private UUID agentUserId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private String currency;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    @Column(name = "needed_by", nullable = false)
    private LocalDate neededBy;

    @Column(name = "available_qty", nullable = false)
    private BigDecimal availableQty;

    @Column(name = "fully_available", nullable = false)
    private boolean fullyAvailable;

    /** False for terminal products: Valio does not know their stock, so availability is an assumption. */
    @Column(name = "stock_confirmed", nullable = false)
    private boolean stockConfirmed = true;

    @Column(name = "earliest_delivery")
    private LocalDate earliestDelivery;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private String signature;
}
