package vn.edu.phenikaa.canteen.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.AuthProperties;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.util.Hashing;
import vn.edu.phenikaa.canteen.model.EmailVerification;
import vn.edu.phenikaa.canteen.model.UserAccount;
import vn.edu.phenikaa.canteen.model.VerificationPurpose;
import vn.edu.phenikaa.canteen.repository.EmailVerificationRepository;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;

/**
 * UC1.2, BR08, NFR04: OTP 6 số, lưu bản băm HMAC, có hạn, dùng một lần, giới hạn số lần thử sai.
 * Email OTP đi qua hàng đợi email (Pha 9): gửi ngay sau commit, lỗi thì thử lại, nội dung bị xóa sau khi gửi.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository verifications;
    private final UserAccountRepository users;
    private final SchoolEmailPolicy emailPolicy;
    private final PromotionService promotions;
    private final AuthProperties props;
    private final NotificationService notifications;
    private final Clock clock;

    /** Người dùng bấm "gửi lại mã" cho email đang chờ xác minh. */
    @Transactional
    public void resend(Long userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getPendingEmail() != null) {
            sendCode(user, user.getPendingEmail(), VerificationPurpose.CHANGE_EMAIL);
        } else if (user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        } else {
            sendCode(user, user.getEmail(), VerificationPurpose.VERIFY);
        }
    }

    /** Sinh mã mới, vô hiệu mã cũ, gửi tới chính địa chỉ cần xác minh. */
    @Transactional
    public void sendCode(UserAccount user, String targetEmail, VerificationPurpose purpose) {
        String email = emailPolicy.normalize(targetEmail);
        if (!emailPolicy.isSchoolEmail(email)) {
            throw new BusinessException(ErrorCode.SCHOOL_EMAIL_REQUIRED);
        }
        LocalDateTime now = LocalDateTime.now(clock);

        verifications.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).ifPresent(last -> {
            if (last.getCreatedAt().plus(props.otpResendCooldown()).isAfter(now)) {
                throw new BusinessException(ErrorCode.OTP_RATE_LIMITED);
            }
        });
        if (verifications.countByUserIdAndCreatedAtAfter(user.getId(), now.minusHours(1)) >= props.otpMaxSendsPerHour()) {
            throw new BusinessException(ErrorCode.OTP_RATE_LIMITED);
        }

        verifications.invalidateOpen(user.getId(), now);   // đổi địa chỉ/gửi mã mới làm vô hiệu mã cũ

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerification v = new EmailVerification();
        v.setUserId(user.getId());
        v.setEmail(email);
        v.setPurpose(purpose);
        v.setCodeHash(hash(user.getId(), email, code));
        v.setExpiresAt(now.plus(props.otpTtl()));
        verifications.save(v);

        // Gửi qua outbox (NFR05): lỗi email không làm hỏng việc tạo mã, và thử lại dùng cùng một dòng (BR29).
        notifications.queueEmail(user.getId(), null, email, "Mã xác minh email Canteen",
                "Mã xác minh của bạn là " + code + ". Mã có hiệu lực " + props.otpTtl().toMinutes()
                        + " phút và chỉ dùng một lần. Nếu bạn không yêu cầu, hãy bỏ qua email này.",
                "OTP:" + v.getId());
    }

    @Transactional
    public void invalidateOpenVerifications(Long userId) {
        verifications.invalidateOpen(userId, LocalDateTime.now(clock));
    }

    /**
     * noRollbackFor: lần nhập sai phải được ghi nhận (tăng attempts) dù ném lỗi, nếu không giới hạn thử sai vô nghĩa.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(Long userId, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        EmailVerification v = verifications
                .findFirstByUserIdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OTP_EXPIRED,
                        "Chưa có mã xác minh hợp lệ. Hãy yêu cầu mã mới."));

        if (!now.isBefore(v.getExpiresAt())) {
            v.setInvalidatedAt(now);
            throw new BusinessException(ErrorCode.OTP_EXPIRED);
        }
        if (v.getAttempts() >= props.otpMaxAttempts()) {
            throw new BusinessException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        }
        if (!Hashing.constantTimeEquals(v.getCodeHash(), hash(userId, v.getEmail(), code))) {
            v.setAttempts(v.getAttempts() + 1);
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }

        UserAccount user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (v.getPurpose() == VerificationPurpose.CHANGE_EMAIL) {
            if (user.getPendingEmail() == null || !user.getPendingEmail().equalsIgnoreCase(v.getEmail())) {
                v.setInvalidatedAt(now);
                throw new BusinessException(ErrorCode.OTP_EXPIRED);
            }
            if (users.findByEmailIgnoreCase(v.getEmail()).filter(o -> !o.getId().equals(userId)).isPresent()) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
            }
            user.setEmail(v.getEmail());
            user.setPendingEmail(null);
        } else if (!user.getEmail().equalsIgnoreCase(v.getEmail())) {
            v.setInvalidatedAt(now);
            throw new BusinessException(ErrorCode.OTP_EXPIRED);
        }
        v.setConsumedAt(now);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        // BR05, BR06: xác minh email trường là lúc cấp quyền ưu đãi; trùng tài khoản hoặc email thì bỏ qua.
        promotions.grantIfEligible(user);
    }

    private String hash(Long userId, String email, String code) {
        return Hashing.hmacSha256Hex(props.otpPepper(), userId + ":" + email + ":" + code);
    }
}
