package vn.edu.phenikaa.canteen.common.dev;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.model.AccountStatus;
import vn.edu.phenikaa.canteen.model.Role;
import vn.edu.phenikaa.canteen.model.UserAccount;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;

/**
 * Chỉ chạy ở profile dev. Tạo tài khoản thử (mật khẩu chung {@value #PASSWORD}) và phân công quầy.
 * Idempotent: chạy lại không tạo trùng.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAccountSeeder implements ApplicationRunner {

    static final String PASSWORD = "Canteen@123";
    private static final Logger log = LoggerFactory.getLogger(DevAccountSeeder.class);

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("admin@canteen.local", "Quản trị viên", Role.ADMIN, true);
        UserAccount manager = seed("manager@canteen.local", "Quản lý quầy", Role.VENDOR_MANAGER, true);
        UserAccount staff = seed("staff@canteen.local", "Nhân viên quầy", Role.VENDOR_STAFF, true);
        seed("sv@phenikaa-uni.edu.vn", "Sinh viên thử", Role.CUSTOMER, true);
        seed("guest@gmail.com", "Khách ngoài trường", Role.CUSTOMER, false);

        assign(manager, "Cơm Nhà Cô Lan", "MANAGER");
        assign(manager, "Bún Phở Ba Sao", "MANAGER");
        assign(staff, "Cơm Nhà Cô Lan", "STAFF");     // staff KHÔNG thuộc Bún Phở Ba Sao: dùng để thử BR03
        log.info("Dev accounts sẵn sàng (mật khẩu chung: {})", PASSWORD);
    }

    private UserAccount seed(String email, String name, Role role, boolean verified) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setEmail(email);
            u.setFullName(name);
            u.setPasswordHash(encoder.encode(PASSWORD));
            u.setRole(role);
            u.setStatus(AccountStatus.ACTIVE);
            u.setEmailVerified(verified);
            u.setEmailVerifiedAt(verified ? LocalDateTime.now(clock) : null);
            return users.save(u);
        });
    }

    private void assign(UserAccount user, String vendorName, String memberRole) {
        jdbc.sql("""
                INSERT INTO vendor_member (user_id, vendor_id, member_role, active)
                SELECT :u, v.id, :r, TRUE FROM vendor v
                 WHERE v.name = :n
                   AND NOT EXISTS (SELECT 1 FROM vendor_member m WHERE m.user_id = :u AND m.vendor_id = v.id)""")
                .param("u", user.getId()).param("r", memberRole).param("n", vendorName)
                .update();
    }
}
