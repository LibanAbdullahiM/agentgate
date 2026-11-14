package fi.agentgate.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, String entityId);
}
