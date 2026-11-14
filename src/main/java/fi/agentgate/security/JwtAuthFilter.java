package fi.agentgate.security;

import java.io.IOException;
import java.util.List;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reads "Authorization: Bearer <jwt>" and puts an {@link AuthPrincipal} into the security context. */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthPrincipal principal = jwtService.parse(header.substring(7));
                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                // invalid or expired token: stay unauthenticated, the entry point answers 401
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
