package vn.edu.phenikaa.canteen.service;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.security.AuthenticatedUser;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.model.Role;

/**
 * Quyền xem xét sự việc (R10.3): quầy chỉ xét sự việc của quầy mình, quản trị viên xét mọi sự việc,
 * và chỉ quản trị viên quyết định hạn chế đặt trước toàn nền tảng (BR31).
 */
@Component
@RequiredArgsConstructor
public class IncidentAccess {

    private final VendorAccessChecker vendorAccess;

    public AuthenticatedUser requireAdmin() {
        AuthenticatedUser user = CurrentUser.get();
        if (user.role() != Role.ADMIN) {
            throw BusinessException.forbidden();
        }
        return user;
    }

    /** Quản trị viên, hoặc quản lý của đúng quầy có sự việc. Nhân viên thường không kết luận sự việc. */
    public void requireReviewer(Long vendorId) {
        requireAdminOrVendorManager(vendorId);
    }

    /** Quản trị viên, hoặc quản lý được phân công đúng quầy này (xem thống kê, kết luận sự việc). */
    public void requireAdminOrVendorManager(Long vendorId) {
        if (CurrentUser.get().role() == Role.ADMIN) {
            return;
        }
        vendorAccess.requireManager(vendorId);
    }
}
