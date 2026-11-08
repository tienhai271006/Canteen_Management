package vn.edu.phenikaa.canteen.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Mã đơn dễ đọc cho khách đọc lại ở quầy, ví dụ {@code ORD-261025-K7M2QX}. Bỏ các ký tự dễ nhầm (0/O, 1/I/L). */
@Component
@RequiredArgsConstructor
public class OrderCodeGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder sb = new StringBuilder("ORD-").append(LocalDate.now(clock).format(DAY)).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
