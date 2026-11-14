package fi.agentgate.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fi.agentgate.security.AuthPrincipal;

@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /** Joins the caller's transaction: the event is kept only if the business change commits. */
    @Transactional
    public void record(AuthPrincipal actor, String action, String entityType, String entityId,
                       String decision, String reason, String details) {
        repository.save(build(actor, action, entityType, entityId, decision, reason, details));
    }

    /** Own transaction: the event survives even if the caller rolls back (e.g. rejected or tampered requests). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuthPrincipal actor, String action, String entityType, String entityId,
                                  String decision, String reason, String details) {
        repository.save(build(actor, action, entityType, entityId, decision, reason, details));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> trace(String entityType, String entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId);
    }

    /** Merges the trails of several entities (e.g. an order and its quote) in time order. */
    @Transactional(readOnly = true)
    public List<AuditEvent> traceAll(List<String[]> entities) {
        List<AuditEvent> all = new ArrayList<>();
        for (String[] e : entities) {
            all.addAll(repository.findByEntityTypeAndEntityIdOrderByIdAsc(e[0], e[1]));
        }
        all.sort(Comparator.comparing(AuditEvent::getId));
        return all;
    }

    private AuditEvent build(AuthPrincipal actor, String action, String entityType, String entityId,
                             String decision, String reason, String details) {
        AuditEvent e = new AuditEvent();
        if (actor != null) {
            e.setActorId(actor.id());
            e.setActorRole(actor.role().name());
        } else {
            e.setActorRole("SYSTEM");
        }
        e.setAction(action);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setDecision(decision);
        e.setReason(reason);
        e.setDetails(details);
        return e;
    }
}
