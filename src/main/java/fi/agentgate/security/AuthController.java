package fi.agentgate.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.agentgate.common.ApiException;
import fi.agentgate.user.AppUser;
import fi.agentgate.user.UserRepository;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds,
                                String username, String role) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByUsername(req.username())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Wrong username or password"));
        return new TokenResponse(jwt.issue(user), "Bearer", jwt.ttlSeconds(),
                user.getUsername(), user.getRole().name());
    }
}
