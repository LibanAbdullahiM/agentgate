package fi.agentgate.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditRepository repository;
    private final AuditNarrator narrator;

    public AuditController(AuditRepository repository, AuditNarrator narrator) {
        this.repository = repository;
        this.narrator = narrator;
    }

    /**
     * Live feed of everything agents and humans do, oldest first.
     * First call: no parameters returns the latest events. Then poll with ?afterId=<highest id you have seen>.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('BUYER_ADMIN','APPROVER')")
    public List<AuditNarrator.AuditView> feed(@RequestParam(required = false) Long afterId,
                                              @RequestParam(defaultValue = "50") int limit) {
        int n = Math.min(Math.max(limit, 1), 200);
        List<AuditEvent> events;
        if (afterId == null) {
            events = new ArrayList<>(repository.findAllByOrderByIdDesc(PageRequest.of(0, n)));
            Collections.reverse(events);
        } else {
            events = repository.findByIdGreaterThanOrderByIdAsc(afterId, PageRequest.of(0, n));
        }
        return narrator.narrate(events);
    }
}
