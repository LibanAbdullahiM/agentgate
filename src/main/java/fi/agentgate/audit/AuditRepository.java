package fi.agentgate.audit;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, String entityId);

    /** Newest first: used for the first load of the live feed. */
    List<AuditEvent> findAllByOrderByIdDesc(Pageable pageable);

    /** Oldest first: used when the frontend polls for events after the last id it has seen. */
    List<AuditEvent> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
