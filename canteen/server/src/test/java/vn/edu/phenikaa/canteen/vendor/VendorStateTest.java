package vn.edu.phenikaa.canteen.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.model.Vendor;
import vn.edu.phenikaa.canteen.model.VendorStatus;

/** Không cần Spring hay Docker: kiểm tra bảng chuyển trạng thái của quầy (R2.2, R2.4). */
class VendorStateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 9, 0);

    @Test
    void approveSuspendReactivateHappyPath() {
        Vendor v = new Vendor();
        assertThat(v.getStatus()).isEqualTo(VendorStatus.PENDING);
        assertThat(v.isActive()).isFalse();

        v.approve(7L, NOW);
        assertThat(v.getStatus()).isEqualTo(VendorStatus.ACTIVE);
        assertThat(v.getApprovedBy()).isEqualTo(7L);
        assertThat(v.getApprovedAt()).isEqualTo(NOW);

        v.suspend("Nghỉ lễ");
        assertThat(v.getStatus()).isEqualTo(VendorStatus.SUSPENDED);
        assertThat(v.getStatusReason()).isEqualTo("Nghỉ lễ");
        assertThat(v.isActive()).isFalse();

        v.reactivate();
        assertThat(v.isActive()).isTrue();
        assertThat(v.getStatusReason()).isNull();
    }

    @Test
    void invalidTransitionsAreRejected() {
        Vendor pending = new Vendor();
        assertInvalid(() -> pending.suspend("x"));
        assertInvalid(pending::reactivate);

        Vendor active = new Vendor();
        active.approve(1L, NOW);
        assertInvalid(() -> active.approve(1L, NOW));
        assertInvalid(active::reactivate);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VENDOR_INVALID_STATE));
    }
}
