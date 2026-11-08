package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.dto.AccountStatusResponse;
import vn.edu.phenikaa.canteen.dto.ProfileResponse;
import vn.edu.phenikaa.canteen.dto.UpdateProfileRequest;
import vn.edu.phenikaa.canteen.model.UserAccount;
import vn.edu.phenikaa.canteen.model.VerificationPurpose;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;

/** UC1.8 và UC1.9. */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserAccountRepository users;
    private final EmailVerificationService verification;
    private final SchoolEmailPolicy emailPolicy;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return ProfileResponse.from(load(userId));
    }

    /**
     * Không cho sửa vai trò, trạng thái hay ưu đãi. Đổi email: không tạo lượt ưu đãi mới vì
     * promotion_entitlement khóa theo tài khoản và email trường (BR06), ProfileService không đụng tới bảng đó.
     */
    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        UserAccount u = load(userId);

        String studentCode = req.studentCode() == null || req.studentCode().isBlank() ? null : req.studentCode().trim();
        if (studentCode != null && users.existsByStudentCodeAndIdNot(studentCode, userId)) {
            throw new BusinessException(ErrorCode.STUDENT_CODE_ALREADY_USED);
        }
        u.setFullName(req.fullName().trim());
        u.setStudentCode(studentCode);
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());

        if (req.email() != null && !req.email().isBlank()) {
            changeEmail(u, emailPolicy.normalize(req.email()));
        }
        return ProfileResponse.from(u);
    }

    private void changeEmail(UserAccount u, String newEmail) {
        if (newEmail.equalsIgnoreCase(u.getEmail())) {
            return;
        }
        if (users.findByEmailIgnoreCase(newEmail).filter(o -> !o.getId().equals(u.getId())).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }
        if (emailPolicy.isSchoolEmail(newEmail)) {
            // Email trường mới: chỉ thay sau khi xác minh OTP gửi tới chính địa chỉ mới.
            u.setPendingEmail(newEmail);
            verification.sendCode(u, newEmail, VerificationPurpose.CHANGE_EMAIL);
        } else {
            // Không thể xác minh email ngoài miền trường: đổi luôn nhưng mất trạng thái đã xác minh.
            u.setEmail(newEmail);
            u.setPendingEmail(null);
            u.setEmailVerified(false);
            u.setEmailVerifiedAt(null);
            verification.invalidateOpenVerifications(u.getId());
        }
    }

    @Transactional(readOnly = true)
    public AccountStatusResponse getAccountStatus(Long userId) {
        UserAccount u = load(userId);
        AccountStatusResponse.PromotionInfo promotion = null;
        AccountStatusResponse.RestrictionInfo restriction = null;
        Integer violations = null;
        boolean available = true;
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            promotion = jdbc.sql("SELECT status, expires_at FROM promotion_entitlement WHERE user_id = :u")
                    .param("u", userId)
                    .query((rs, n) -> new AccountStatusResponse.PromotionInfo(
                            rs.getString("status"), rs.getObject("expires_at", LocalDateTime.class)))
                    .optional().orElse(null);
            restriction = jdbc.sql("""
                            SELECT ends_at, reason FROM restriction
                             WHERE user_id = :u AND status = 'ACTIVE' AND ends_at > :now
                             ORDER BY ends_at DESC LIMIT 1""")
                    .param("u", userId).param("now", now)
                    .query((rs, n) -> new AccountStatusResponse.RestrictionInfo(
                            rs.getObject("ends_at", LocalDateTime.class), rs.getString("reason")))
                    .optional().orElse(null);
            // BR26: chỉ vi phạm Đã xác nhận trong 30 ngày gần nhất.
            violations = jdbc.sql("""
                            SELECT COUNT(*) FROM incident
                             WHERE user_id = :u AND status = 'CONFIRMED' AND occurred_at >= :since""")
                    .param("u", userId).param("since", now.minusDays(30))
                    .query(Integer.class).single();
        } catch (DataAccessException e) {
            // UC1.9: không tải được lịch sử thì nói rõ là chưa khả dụng, không mặc định "không vi phạm".
            log.warn("Không tải được lịch sử tài khoản của user {}: {}", userId, e.getMessage());
            available = false;
            promotion = null;
            restriction = null;
            violations = null;
        }
        return new AccountStatusResponse(u.getEmail(), u.getPendingEmail(), u.isEmailVerified(),
                emailPolicy.isSchoolEmail(u.getEmail()), u.getStatus(), available, promotion, restriction, violations);
    }

    private UserAccount load(Long userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
