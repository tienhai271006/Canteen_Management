package vn.edu.phenikaa.canteen.dto;

import java.time.LocalTime;

import vn.edu.phenikaa.canteen.model.PickupSlot;

public record SlotResponse(Long id, LocalTime startTime, LocalTime endTime) {

    public static SlotResponse from(PickupSlot s) {
        return new SlotResponse(s.getId(), s.getStartTime(), s.getEndTime());
    }
}
