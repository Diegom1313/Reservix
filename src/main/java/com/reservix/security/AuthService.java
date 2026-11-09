package com.reservix.security;

import com.reservix.domain.AppUser;
import com.reservix.domain.Role;
import com.reservix.repository.UserRepository;
import com.reservix.service.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwt;
    private final Duration ttl;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtEncoder jwt,
                       @Value("${reservix.jwt.ttl-minutes}") long ttlMinutes) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Transactional
    public String register(String name, String email, String password) {
        String normalized = email.toLowerCase(Locale.ROOT);
        if (users.existsByEmail(normalized)) {
            throw new ConflictException("Ese correo ya está registrado");
        }
        return token(users.save(new AppUser(name, normalized, encoder.encode(password), Role.CUSTOMER)));
    }

    @Transactional(readOnly = true)
    public String login(String email, String password) {
        AppUser user = users.findByEmail(email.toLowerCase(Locale.ROOT))
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));
        return token(user);
    }

    private String token(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("role", user.getRole().name())
                .build();
        return jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
