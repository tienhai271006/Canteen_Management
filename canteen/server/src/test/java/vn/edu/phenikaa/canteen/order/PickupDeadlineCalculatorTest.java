package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import vn.edu.phenikaa.canteen.common.config.OrderProperties;
import vn.edu.phenikaa.canteen.service.PickupDeadlineCalculator;

/** BR22, đối chiếu Bảng 21 trong tài liệu. Không cần Docker. */
class PickupDeadlineCalculatorTest {

    private final PickupDeadlineCalculator calc =
            new PickupDeadlineCalculator(new OrderProperties(Duration.ofMinutes(5), 20, Duration.ofMinutes(15)));
    private final LocalDate day = LocalDate.of(2026, 11, 2);

    @Test
    void readyBeforeSlotEndUsesSlotEnd() {
        LocalDateTime ready = day.atTime(11, 45);
        assertThat(calc.compute(day, LocalTime.NOON, ready)).isEqualTo(day.atTime(12, 15));
    }

    @Test
    void readyAfterSlotEndUsesReadyTime() {
        LocalDateTime ready = day.atTime(12, 10);
        assertThat(calc.compute(day, LocalTime.NOON, ready)).isEqualTo(day.atTime(12, 25));
    }

    @Test
    void readyExactlyAtSlotEnd() {
        assertThat(calc.compute(day, LocalTime.NOON, day.atTime(12, 0))).isEqualTo(day.atTime(12, 15));
    }
}
