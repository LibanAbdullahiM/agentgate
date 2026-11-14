package fi.agentgate.mandate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.agentgate.common.ApiException;
import fi.agentgate.security.AuthPrincipal;

@RestController
@RequestMapping("/api/v1/mandates")
@Tag(name = "Mandates")
@SecurityRequirement(name = "bearerAuth")
public class MandateController {

    public record MandateView(UUID id, String buyerName, BigDecimal maxOrderValue, boolean allowSubstitutions,
                              List<String> allowedCategories, Instant expiresAt, boolean active) {
    }

    private final MandateRepository mandates;

    public MandateController(MandateRepository mandates) {
        this.mandates = mandates;
    }

    /** The agent can read its own limits, so it can plan within them. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('AGENT')")
    public MandateView mine(@AuthenticationPrincipal AuthPrincipal principal) {
        Mandate m = mandates.findFirstByAgentUserIdAndActiveTrue(principal.id())
                .orElseThrow(() -> ApiException.notFound("NO_ACTIVE_MANDATE", "This agent has no active mandate"));
        List<String> cats = (m.getAllowedCategories() == null || m.getAllowedCategories().isBlank())
                ? List.of()
                : List.of(m.getAllowedCategories().split(",")).stream().map(String::trim).toList();
        return new MandateView(m.getId(), m.getBuyerName(), m.getMaxOrderValue(), m.isAllowSubstitutions(),
                cats, m.getExpiresAt(), m.isActive());
    }
}
