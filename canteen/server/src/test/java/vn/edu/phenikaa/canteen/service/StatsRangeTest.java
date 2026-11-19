package vn.edu.phenikaa.canteen.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import vn.edu.phenikaa.canteen.common.config.TimeConfig;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;

/** Chuẩn hóa khoảng ngày của thống kê, không cần DB. */
class StatsRangeTest {

    // 2026-03-10 03:00 UTC = 10:00 giờ Việt Nam
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-10T03:00:00Z"), TimeConfig.ZONE);
    private final StatsService service = new StatsService(null, null, clock);

    @Test
    void defaultsToLastSevenDaysEndingToday() {
        LocalDate[] r = service.resolve(null, null);
        assertThat(r[1]).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(r[0]).isEqualTo(LocalDate.of(2026, 3, 4));
    }

    @Test
    void keepsExplicitRange() {
        LocalDate[] r = service.resolve(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(r[0]).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r[1]).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> service.resolve(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void rejectsRangeLongerThanAYear() {
        assertThat(service.resolve(LocalDate.of(2025, 3, 10), LocalDate.of(2026, 3, 10))).hasSize(2);   // đúng 366 ngày
        assertThatThrownBy(() -> service.resolve(LocalDate.of(2025, 3, 9), LocalDate.of(2026, 3, 10)))
                .isInstanceOf(BusinessException.class);
    }
}
