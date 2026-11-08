package vn.edu.phenikaa.canteen.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.dto.AssignMemberRequest;
import vn.edu.phenikaa.canteen.dto.MemberResponse;
import vn.edu.phenikaa.canteen.model.AccountStatus;
import vn.edu.phenikaa.canteen.model.MemberRole;
import vn.edu.phenikaa.canteen.model.Role;
import vn.edu.phenikaa.canteen.model.UserAccount;
import vn.edu.phenikaa.canteen.model.Vendor;
import vn.edu.phenikaa.canteen.model.VendorMember;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;
import vn.edu.phenikaa.canteen.repository.VendorMemberRepository;
import vn.edu.phenikaa.canteen.repository.VendorRepository;

/**
 * R2.5: gán nhân sự cho quầy. Hai đường vào, quyền khác nhau:
 * <ul>
 *   <li>Quản lý quầy: chỉ thêm hoặc gỡ NHÂN VIÊN (STAFF), và chỉ với tài khoản đã mang vai trò nhân viên quầy.</li>
 *   <li>Admin: thêm bất kỳ vai trò nào, kể cả quản lý, và nâng vai trò tài khoản nếu cần
 *       (CUSTOMER lên VENDOR_STAFF, hoặc lên VENDOR_MANAGER). Không đụng tới tài khoản ADMIN.</li>
 * </ul>
 * Quầy luôn phải còn ít nhất một quản lý.
 */
@Service
@RequiredArgsConstructor
public class VendorMemberService {

    private final VendorMemberRepository members;
    private final VendorRepository vendors;
    private final UserAccountRepository users;
    private final VendorAccessChecker access;

    @Transactional(readOnly = true)
    public List<MemberResponse> listForManager(Long vendorId) {
        access.requireManager(vendorId);
        return list(vendorId);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listForAdmin(Long vendorId) {
        requireVendor(vendorId);
        return list(vendorId);
    }

    @Transactional
    public MemberResponse assignAsManager(Long vendorId, AssignMemberRequest req) {
        access.requireManager(vendorId);
        if (req.memberRole() != MemberRole.STAFF) {
            throw BusinessException.forbidden();      // chỉ admin được bổ nhiệm quản lý
        }
        return assign(vendorId, req, false);
    }

    @Transactional
    public MemberResponse assignAsAdmin(Long vendorId, AssignMemberRequest req) {
        return assign(vendorId, req, true);
    }

    @Transactional
    public void removeAsManager(Long vendorId, Long memberId) {
        access.requireManager(vendorId);
        VendorMember member = members.findByIdAndVendorId(memberId, vendorId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy nhân sự trong quầy."));
        if (member.getMemberRole() == MemberRole.MANAGER) {
            throw BusinessException.forbidden();      // chỉ admin được gỡ quản lý
        }
        deactivate(member);
    }

    @Transactional
    public void removeAsAdmin(Long vendorId, Long memberId) {
        VendorMember member = members.findByIdAndVendorId(memberId, vendorId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy nhân sự trong quầy."));
        deactivate(member);
    }

    // ---------------------------------------------------------------

    private MemberResponse assign(Long vendorId, AssignMemberRequest req, boolean byAdmin) {
        Vendor vendor = requireVendor(vendorId);
        UserAccount user = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy tài khoản với email này."));
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        MemberRole target = req.memberRole();
        ensureAccountRole(user, target, byAdmin);

        VendorMember member = members.findByUserIdAndVendorId(user.getId(), vendorId).orElse(null);
        if (member == null) {
            member = new VendorMember();
            member.setUser(user);
            member.setVendor(vendor);
        } else if (member.isActive()) {
            // Đã là thành viên: chỉ admin mới được đổi vai trò trong quầy, và đổi sang cùng vai trò là thừa.
            if (!byAdmin || member.getMemberRole() == target) {
                throw new BusinessException(ErrorCode.MEMBER_ALREADY_ASSIGNED);
            }
            if (member.getMemberRole() == MemberRole.MANAGER) {
                requireAnotherManager(vendorId);       // hạ quản lý cuối cùng xuống nhân viên
            }
        }
        member.setMemberRole(target);
        member.setActive(true);
        return MemberResponse.from(members.save(member));
    }

    /** Bảo đảm vai trò tài khoản đủ để giữ vị trí trong quầy; admin được phép nâng vai trò. */
    private void ensureAccountRole(UserAccount user, MemberRole target, boolean byAdmin) {
        Role role = user.getRole();
        if (role == Role.ADMIN) {
            throw new BusinessException(ErrorCode.MEMBER_ROLE_MISMATCH, "Tài khoản quản trị không vận hành quầy.");
        }
        boolean enough = target == MemberRole.STAFF
                ? role == Role.VENDOR_STAFF || role == Role.VENDOR_MANAGER
                : role == Role.VENDOR_MANAGER;
        if (enough) {
            return;
        }
        if (!byAdmin) {
            throw new BusinessException(ErrorCode.MEMBER_ROLE_MISMATCH,
                    "Tài khoản chưa có vai trò nhân viên quầy, hãy nhờ quản trị viên cấp quyền.");
        }
        user.setRole(target == MemberRole.MANAGER ? Role.VENDOR_MANAGER : Role.VENDOR_STAFF);
    }

    private void deactivate(VendorMember member) {
        if (!member.isActive()) {
            return;                                    // gỡ hai lần không lỗi
        }
        if (member.getMemberRole() == MemberRole.MANAGER) {
            requireAnotherManager(member.getVendor().getId());
        }
        member.setActive(false);
    }

    private void requireAnotherManager(Long vendorId) {
        if (members.countByVendorIdAndMemberRoleAndActiveTrue(vendorId, MemberRole.MANAGER) <= 1) {
            throw new BusinessException(ErrorCode.LAST_MANAGER_REQUIRED);
        }
    }

    private List<MemberResponse> list(Long vendorId) {
        return members.findActiveByVendorId(vendorId).stream().map(MemberResponse::from).toList();
    }

    private Vendor requireVendor(Long vendorId) {
        return vendors.findById(vendorId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy quầy."));
    }
}
