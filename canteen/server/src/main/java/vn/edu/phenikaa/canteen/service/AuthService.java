package vn.edu.phenikaa.canteen.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.JwtProperties;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.JwtService;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.common.util.Hashing;
import vn.edu.phenikaa.canteen.dto.AuthUser;
import vn.edu.phenikaa.canteen.dto.ChangePasswordRequest;
import vn.edu.phenikaa.canteen.dto.LoginRequest;
import vn.edu.phenikaa.canteen.dto.RegisterRequest;
import vn.edu.phenikaa.canteen.dto.TokenResponse;
import vn.edu.phenikaa.canteen.model.AccountStatus;
import vn.edu.phenikaa.canteen.model.RefreshToken;
import vn.edu.phenikaa.canteen.model.Role;
import vn.edu.phenikaa.canteen.model.UserAccount;
import vn.edu.phenikaa.canteen.model.VerificationPurpose;
import vn.edu.phenikaa.canteen.repository.RefreshTokenRepository;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;

/** UC1.1, UC1.5, UC1.6, UC1.7. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties jwtProps;
    private final SchoolEmailPolicy emailPolicy;
    private final EmailVerificationService verification;
    private final VendorAccessChecker vendorAccess;
    private final Clock clock;

    /** Băm giả để thời gian phản hồi khi email không tồn tại gần giống khi sai mật khẩu. */
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        String email = emailPolicy.normalize(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }
        String studentCode = blankToNull(req.studentCode());
        if (studentCode != null && users.existsByStudentCode(studentCode)) {
            throw new BusinessException(ErrorCode.STUDENT_CODE_ALREADY_USED);
        }

        UserAccount u = new UserAccount();
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.CUSTOMER);               // tự đăng ký luôn là khách; vai trò quầy do quản lý/admin cấp
        u.setStatus(AccountStatus.ACTIVE);
        u.setStudentCode(studentCode);
        users.saveAndFlush(u);

        // Email ngoài miền trường vẫn đăng ký được nhưng không có đặc quyền email trường (UC1.1).
        if (emailPolicy.isSchoolEmail(email)) {
            verification.sendCode(u, email, VerificationPurpose.VERIFY);
        }
        return issueTokens(u);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        UserAccount u = users.findByEmailIgnoreCase(emailPolicy.normalize(req.email())).orElse(null);
        boolean passwordOk = encoder.matches(req.password(), u != null ? u.getPasswordHash() : dummyHash);
        if (u == null || !passwordOk) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (u.getStatus() == AccountStatus.LOCKED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        // Hạn chế đặt trước (BR31) cố ý không chặn đăng nhập ở đây.
        u.setLastLoginAt(LocalDateTime.now(clock));
        return issueTokens(u);
    }

    /** noRollbackFor: phát hiện dùng lại token đã thu hồi thì việc thu hồi toàn bộ phiên phải được lưu. */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken stored = refreshTokens.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (stored.getRevokedAt() != null) {
            log.warn("Refresh token đã thu hồi bị dùng lại, thu hồi mọi phiên của user {}", stored.getUserId());
            refreshTokens.revokeAllByUserId(stored.getUserId(), now);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!now.isBefore(stored.getExpiresAt())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        UserAccount u = users.findById(stored.getUserId())
                .filter(x -> x.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        stored.setRevokedAt(now);               // xoay vòng: mỗi refresh token dùng một lần
        return issueTokens(u);
    }

    /** Đăng xuất không bao giờ lỗi nghiệp vụ: phiên hết hạn vẫn hoàn tất (UC1.6). */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(LocalDateTime.now(clock)));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Xác nhận mật khẩu không khớp.");
        }
        if (req.newPassword().equals(req.currentPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        refreshTokens.revokeAllByUserId(userId, LocalDateTime.now(clock));   // các phiên khác phải đăng nhập lại
        log.info("Security event: user {} đã đổi mật khẩu", userId);
    }

    private TokenResponse issueTokens(UserAccount u) {
        LocalDateTime now = LocalDateTime.now(clock);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(u.getId());
        rt.setTokenHash(Hashing.sha256Hex(raw));
        rt.setExpiresAt(now.plus(jwtProps.refreshTtl()));
        refreshTokens.save(rt);

        AuthUser authUser = new AuthUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole(),
                u.isEmailVerified(), vendorAccess.vendorIdsOf(u.getId()));
        return TokenResponse.of(jwt.issueAccessToken(u), raw, jwt.accessTtlSeconds(), authUser);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
