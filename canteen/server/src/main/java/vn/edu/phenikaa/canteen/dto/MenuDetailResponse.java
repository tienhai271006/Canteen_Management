package vn.edu.phenikaa.canteen.dto;

import java.util.List;

public record MenuDetailResponse(
        MenuSummaryResponse menu,
        List<MenuItemResponse> items,
        List<SlotResponse> slots) {
}
