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
import vn.edu.phenikaa.canteen.service.PromotionService;

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
    private final PromotionService promotions;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("admin@canteen.local", "Quản trị viên", Role.ADMIN, true);
        UserAccount manager = seed("manager@canteen.local", "Quản lý quầy", Role.VENDOR_MANAGER, true);
        UserAccount staff = seed("staff@canteen.local", "Nhân viên quầy", Role.VENDOR_STAFF, true);
        UserAccount student = seed("sv@phenikaa-uni.edu.vn", "Sinh viên thử", Role.CUSTOMER, true);
        seed("guest@gmail.com", "Khách ngoài trường", Role.CUSTOMER, false);

        assign(manager, "Cơm Nhà Cô Lan", "MANAGER");
        assign(manager, "Bún Phở Ba Sao", "MANAGER");
        assign(staff, "Cơm Nhà Cô Lan", "STAFF");     // staff KHÔNG thuộc Bún Phở Ba Sao: dùng để thử BR03
        seedCampaignAndStudentEntitlement(student);
        log.info("Dev accounts sẵn sàng (mật khẩu chung: {})", PASSWORD);
    }

    /**
     * Chiến dịch mặc định của đề tài (50%, tối đa 30.000đ, đơn từ 25.000đ, hạn 7 ngày) và quyền ưu đãi cho sv@.
     * Quyền của tài khoản thử được kéo dài mỗi lần khởi động để thử giao diện không bị hết hạn sau 7 ngày.
     */
    private void seedCampaignAndStudentEntitlement(UserAccount student) {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.sql("""
                INSERT INTO campaign (name, discount_percent, max_discount_amount, min_order_amount,
                                      entitlement_valid_days, starts_at, ends_at, active)
                SELECT 'Ưu đãi đơn đầu', 50, 30000, 25000, 7, :s, :e, TRUE FROM DUAL
                 WHERE NOT EXISTS (SELECT 1 FROM campaign)""")
                .param("s", now.minusDays(1)).param("e", now.plusYears(1)).update();
        promotions.grantIfEligible(student);
        jdbc.sql("UPDATE promotion_entitlement SET expires_at = :far WHERE user_id = :u AND status = 'AVAILABLE' "
                        + "AND expires_at < :far")
                .param("far", now.plusDays(365)).param("u", student.getId()).update();
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
