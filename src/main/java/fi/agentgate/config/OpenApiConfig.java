package fi.agentgate.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "AgentGate API",
        version = "0.1.0",
        description = "Trusted trade gateway: lets external purchasing agents search, quote and order "
                + "from a wholesaler under explicit mandates, human approval and a full audit trail."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
