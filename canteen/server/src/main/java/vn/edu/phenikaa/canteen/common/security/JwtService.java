package vn.edu.phenikaa.canteen.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import vn.edu.phenikaa.canteen.common.config.JwtProperties;
import vn.edu.phenikaa.canteen.model.UserAccount;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        byte[] secret = props.secret() == null ? new byte[0] : props.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret phải dài ít nhất 32 ký tự (đặt biến JWT_SECRET).");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTtl = props.accessTtl();
        this.clock = clock;
    }

    public String issueAccessToken(UserAccount user) {
        Date now = Date.from(clock.instant());
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(Date.from(clock.instant().plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    /** Trả về id người dùng nếu token hợp lệ và chưa hết hạn, ngược lại rỗng. */
    public Optional<Long> parseUserId(String token) {
        try {
            String sub = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.valueOf(sub));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
