package vn.edu.phenikaa.canteen.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.AuthProperties;

/** Chuẩn hóa email và nhận biết email thuộc miền của trường (cấu hình app.auth.school-email-domains). */
@Component
@RequiredArgsConstructor
public class SchoolEmailPolicy {

    private final AuthProperties props;

    public String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isSchoolEmail(String email) {
        String e = normalize(email);
        int at = e == null ? -1 : e.lastIndexOf('@');
        if (at < 1) {
            return false;
        }
        String domain = e.substring(at + 1);
        return props.schoolEmailDomains().stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }
}
