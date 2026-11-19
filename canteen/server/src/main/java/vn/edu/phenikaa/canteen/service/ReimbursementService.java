package vn.edu.phenikaa.canteen.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.ReimbursementRequest;

/** Ghi nhận nền tảng đã chuyển khoản bù ưu đãi cho quầy. Chỉ quản trị viên; chỉ ghi thêm, không sửa hay xóa. */
@Service
@RequiredArgsConstructor
public class ReimbursementService {

    private final JdbcClient jdbc;
    private final IncidentAccess access;

    @Transactional
    public long record(ReimbursementRequest req) {
        access.requireAdmin();
        Integer vendor = jdbc.sql("SELECT COUNT(*) FROM vendor WHERE id = :id").param("id", req.vendorId())
                .query(Integer.class).single();
        if (vendor == 0) {
            throw BusinessException.notFound("Không tìm thấy quầy.");
        }
        var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.sql("INSERT INTO platform_reimbursement (vendor_id, for_date, amount, note, recorded_by) "
                        + "VALUES (:v, :d, :a, :n, :u)")
                .param("v", req.vendorId()).param("d", req.forDate()).param("a", req.amount())
                .param("n", req.note()).param("u", CurrentUser.id())
                .update(keys);
        return keys.getKey().longValue();
    }
}
