package fi.agentgate.mandate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.agentgate.audit.AuditService;
import fi.agentgate.common.ApiException;
import fi.agentgate.security.AuthPrincipal;

@RestController
@RequestMapping("/api/v1/mandates")
@Tag(name = "Mandates")
@SecurityRequirement(name = "bearerAuth")
public class MandateController {

    public record MandateView(UUID id, UUID agentUserId, String buyerName, BigDecimal maxOrderValue,
                              boolean allowSubstitutions, List<String> allowedCategories,
                              List<String> avoidAllergens, Instant expiresAt, boolean active) {
    }

    /** Every field is optional: null = leave unchanged. An empty list clears a list. */
    public record MandateUpdate(@DecimalMin(value = "0.01", message = "must be greater than 0") BigDecimal maxOrderValue,
                                Boolean allowSubstitutions, List<String> allowedCategories,
                                List<String> avoidAllergens, Instant expiresAt, Boolean active) {
    }

    private final MandateRepository mandates;
    private final AuditService audit;

    public MandateController(MandateRepository mandates, AuditService audit) {
        this.mandates = mandates;
        this.audit = audit;
    }

    /** The agent can read its own limits, so it can plan within them. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('AGENT')")
    public MandateView mine(@AuthenticationPrincipal AuthPrincipal principal) {
        Mandate m = mandates.findFirstByAgentUserIdAndActiveTrue(principal.id())
                .orElseThrow(() -> ApiException.notFound("NO_ACTIVE_MANDATE", "This agent has no active mandate"));
        return view(m);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUYER_ADMIN','APPROVER')")
    public List<MandateView> list() {
        return mandates.findAll().stream().map(MandateController::view).toList();
    }

    /** The buyer's administrator changes what an agent may decide alone. Every change is audited. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('BUYER_ADMIN')")
    @Transactional
    public MandateView update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                              @Valid @RequestBody MandateUpdate req) {
        Mandate m = mandates.findById(id)
                .orElseThrow(() -> ApiException.notFound("MANDATE_NOT_FOUND", "No mandate with id " + id));
        List<String> changes = new ArrayList<>();
        if (req.maxOrderValue() != null) {
            m.setMaxOrderValue(req.maxOrderValue().setScale(2, RoundingMode.HALF_UP));
            changes.add("maxOrderValue=" + m.getMaxOrderValue());
        }
        if (req.allowSubstitutions() != null) {
            m.setAllowSubstitutions(req.allowSubstitutions());
            changes.add("allowSubstitutions=" + req.allowSubstitutions());
        }
        if (req.allowedCategories() != null) {
            m.setAllowedCategories(csv(req.allowedCategories(), false));
            changes.add("allowedCategories=" + req.allowedCategories());
        }
        if (req.avoidAllergens() != null) {
            m.setAvoidAllergens(csv(req.avoidAllergens(), true));
            changes.add("avoidAllergens=" + req.avoidAllergens());
        }
        if (req.expiresAt() != null) {
            m.setExpiresAt(req.expiresAt());
            changes.add("expiresAt=" + req.expiresAt());
        }
        if (req.active() != null) {
            m.setActive(req.active());
            changes.add("active=" + req.active());
        }
        mandates.save(m);
        audit.record(principal, "MANDATE_UPDATED", "MANDATE", m.getId().toString(), null,
                "The mandate for " + m.getBuyerName() + " was changed.",
                changes.isEmpty() ? "no changes" : String.join("; ", changes));
        return view(m);
    }

    private static String csv(List<String> items, boolean upperCase) {
        List<String> clean = items.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .map(s -> upperCase ? s.toUpperCase() : s)
                .toList();
        return clean.isEmpty() ? null : String.join(",", clean);
    }

    private static MandateView view(Mandate m) {
        return new MandateView(m.getId(), m.getAgentUserId(), m.getBuyerName(), m.getMaxOrderValue(),
                m.isAllowSubstitutions(), m.categoryList(), m.avoidAllergenList(), m.getExpiresAt(), m.isActive());
    }
}
