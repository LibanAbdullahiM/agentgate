package fi.agentgate.user;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import fi.agentgate.mandate.Mandate;
import fi.agentgate.mandate.MandateRepository;

/** Creates demo users and one agent mandate on an empty database. Disable with SEED_ENABLED=false. */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final MandateRepository mandates;
    private final PasswordEncoder encoder;
    private final String password;

    public DemoDataSeeder(UserRepository users, MandateRepository mandates, PasswordEncoder encoder,
                          @Value("${app.seed.password}") String password) {
        this.users = users;
        this.mandates = mandates;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        create("buyer-admin", Role.BUYER_ADMIN, "Buyer Admin");
        create("approver", Role.APPROVER, "Kitchen Manager (approver)");
        AppUser agent = create("agent-1", Role.AGENT, "Purchasing Agent 1");

        Mandate m = new Mandate();
        m.setId(UUID.randomUUID());
        m.setAgentUserId(agent.getId());
        m.setBuyerName("Demo Hotels Oy");
        m.setMaxOrderValue(new BigDecimal("500.00"));
        m.setAllowSubstitutions(false);
        m.setActive(true);
        mandates.save(m);

        log.warn("Seeded demo users buyer-admin / approver / agent-1 with the SEED_PASSWORD. "
                + "Set SEED_ENABLED=false outside of local development.");
    }

    private AppUser create(String username, Role role, String displayName) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setDisplayName(displayName);
        return users.save(u);
    }
}
