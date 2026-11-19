package vn.edu.phenikaa.canteen.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import vn.edu.phenikaa.canteen.model.PickupSlot;

class SlotCapacityResponseTest {

    private PickupSlot slot(Integer cap, int reserved) {
        PickupSlot s = new PickupSlot();
        s.setStartTime(LocalTime.of(11, 0));
        s.setEndTime(LocalTime.of(11, 30));
        org.springframework.test.util.ReflectionTestUtils.setField(s, "capacity", cap);
        org.springframework.test.util.ReflectionTestUtils.setField(s, "reservedQty", reserved);
        return s;
    }

    @Test
    void remaining_is_capacity_minus_held_minus_used() {
        var r = SlotCapacityResponse.of(slot(40, 30), 12);
        assertThat(r.used()).isEqualTo(12);
        assertThat(r.held()).isEqualTo(18);
        assertThat(r.remaining()).isEqualTo(10);
        assertThat(r.full()).isFalse();
    }

    @Test
    void full_when_reserved_reaches_capacity() {
        var r = SlotCapacityResponse.of(slot(30, 30), 0);
        assertThat(r.remaining()).isZero();
        assertThat(r.full()).isTrue();
    }

    @Test
    void unlimited_slot_has_null_remaining_and_is_never_full() {
        var r = SlotCapacityResponse.of(slot(null, 500), 0);
        assertThat(r.capacity()).isNull();
        assertThat(r.remaining()).isNull();
        assertThat(r.full()).isFalse();
    }
}
