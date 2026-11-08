package vn.edu.phenikaa.canteen.common.security;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;

/**
 * NFR01, BR03: nhân viên và quản lý chỉ truy cập quầy được phân công.
 * MỌI API của quầy phải gọi lớp này với vendorId lấy từ dữ liệu (đơn, món, thực đơn),
 * không tin vendorId trên URL. Quản trị viên không vận hành quầy nên cũng không qua được kiểm tra này.
 * Dùng JDBC trực tiếp để không phụ thuộc entity của Pha 4.
 */
@Component
@RequiredArgsConstructor
public class VendorAccessChecker {

    private final JdbcClient jdbc;

    public void requireMember(Long vendorId) {
        requireMember(CurrentUser.id(), vendorId);
    }

    public void requireMember(Long userId, Long vendorId) {
        if (!isMember(userId, vendorId, false)) {
            throw BusinessException.forbidden();
        }
    }

    public void requireManager(Long vendorId) {
        requireManager(CurrentUser.id(), vendorId);
    }

    public void requireManager(Long userId, Long vendorId) {
        if (!isMember(userId, vendorId, true)) {
            throw BusinessException.forbidden();
        }
    }

    public List<Long> vendorIdsOf(Long userId) {
        return jdbc.sql("SELECT vendor_id FROM vendor_member WHERE user_id = :u AND active = TRUE ORDER BY vendor_id")
                .param("u", userId)
                .query(Long.class)
                .list();
    }

    private boolean isMember(Long userId, Long vendorId, boolean managerOnly) {
        if (userId == null || vendorId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM vendor_member WHERE user_id = :u AND vendor_id = :v AND active = TRUE"
                + (managerOnly ? " AND member_role = 'MANAGER'" : "");
        Long count = jdbc.sql(sql).param("u", userId).param("v", vendorId).query(Long.class).single();
        return count != null && count > 0;
    }
}
