package vn.edu.phenikaa.canteen.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import vn.edu.phenikaa.canteen.model.PickupSlot;
import vn.edu.phenikaa.canteen.model.Quota;

/** Quy tắc nhỏ không cần Spring: trùng khung giờ và số suất đặt online còn lại. */
class SlotAndLikeTest {

    private static PickupSlot slot(String from, String to) {
        PickupSlot s = new PickupSlot();
        s.setStartTime(LocalTime.parse(from));
        s.setEndTime(LocalTime.parse(to));
        return s;
    }

    @Test
    void adjacentSlotsDoNotOverlapButIntersectingOnesDo() {
        PickupSlot s = slot("11:00", "11:30");
        assertThat(s.overlaps(LocalTime.parse("11:30"), LocalTime.parse("12:00"))).isFalse();
        assertThat(s.overlaps(LocalTime.parse("10:30"), LocalTime.parse("11:00"))).isFalse();
        assertThat(s.overlaps(LocalTime.parse("11:15"), LocalTime.parse("11:45"))).isTrue();
        assertThat(s.overlaps(LocalTime.parse("10:00"), LocalTime.parse("12:00"))).isTrue();
    }

    @Test
    void availableOnlineExcludesCounterShareAndReserved() {
        Quota q = new Quota();
        q.setTotalSlots(30);
        q.setCounterSlots(5);
        q.setReservedSlots(10);
        assertThat(q.availableOnline()).isEqualTo(15);
    }
}
