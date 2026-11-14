package fi.agentgate.mandate;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MandateRepository extends JpaRepository<Mandate, UUID> {

    Optional<Mandate> findFirstByAgentUserIdAndActiveTrue(UUID agentUserId);
}
