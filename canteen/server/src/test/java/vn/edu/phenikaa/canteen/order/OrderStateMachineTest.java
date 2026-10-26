package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.service.OrderStateMachine;

/** Không cần Spring: bảng chuyển trạng thái là dữ liệu thuần. */
class OrderStateMachineTest {

    private final OrderStateMachine machine = new OrderStateMachine();

    @Test
    void happyPathIsAllowed() {
        assertThat(machine.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED)).isTrue();
        assertThat(machine.canTransition(OrderStatus.CONFIRMED, OrderStatus.PREPARING)).isTrue();
        assertThat(machine.canTransition(OrderStatus.PREPARING, OrderStatus.READY)).isTrue();
        assertThat(machine.canTransition(OrderStatus.READY, OrderStatus.COMPLETED)).isTrue();
    }

    @Test
    void pendingCanBeCancelledOrExpiredButNotSkipAhead() {
        assertThat(machine.nextOf(OrderStatus.PENDING))
                .containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.EXPIRED);
        assertThat(machine.canTransition(OrderStatus.PENDING, OrderStatus.READY)).isFalse();
        assertThat(machine.canTransition(OrderStatus.PENDING, OrderStatus.COMPLETED)).isFalse();
    }

    @Test
    void readyOrderEndsAsCompletedOrNotCollected() {
        assertThat(machine.nextOf(OrderStatus.READY))
                .containsExactlyInAnyOrder(OrderStatus.COMPLETED, OrderStatus.NOT_COLLECTED);
        assertThat(machine.canTransition(OrderStatus.PREPARING, OrderStatus.NOT_COLLECTED)).isFalse();
    }

    @Test
    void terminalStatesCannotGoAnywhere() {
        Set<OrderStatus> terminal = EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED,
                OrderStatus.EXPIRED, OrderStatus.NOT_COLLECTED);
        for (OrderStatus from : OrderStatus.values()) {
            assertThat(machine.isTerminal(from)).isEqualTo(terminal.contains(from));
            if (terminal.contains(from)) {
                for (OrderStatus to : OrderStatus.values()) {
                    assertThat(machine.canTransition(from, to)).as(from + " -> " + to).isFalse();
                }
            }
        }
    }

    @Test
    void noStatusTransitionsToItself() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(machine.canTransition(s, s)).isFalse();
        }
    }

    @Test
    void invalidTransitionThrowsBusinessException() {
        assertThatThrownBy(() -> machine.require(OrderStatus.COMPLETED, OrderStatus.CANCELLED))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_TRANSITION));
    }

    /** BR14 và BR15: chỉ hủy và hết hạn mới trả suất; không nhận món thì không tự cộng lại. */
    @Test
    void onlyCancelledAndExpiredReleaseQuota() {
        for (OrderStatus s : OrderStatus.values()) {
            boolean expected = s == OrderStatus.CANCELLED || s == OrderStatus.EXPIRED;
            assertThat(machine.releasesQuota(s)).as(s.name()).isEqualTo(expected);
        }
    }
}
