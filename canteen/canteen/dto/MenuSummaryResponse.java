package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import vn.edu.phenikaa.canteen.model.MealSession;
import vn.edu.phenikaa.canteen.model.Menu;
import vn.edu.phenikaa.canteen.model.MenuStatus;

public record MenuSummaryResponse(
        Long id,
        Long vendorId,
        LocalDate menuDate,
        MealSession mealSession,
        LocalTime orderCutoffTime,
        MenuStatus status) {

    public static MenuSummaryResponse from(Menu m) {
        return new MenuSummaryResponse(m.getId(), m.getVendor().getId(), m.getMenuDate(), m.getMealSession(),
                m.getOrderCutoffTime(), m.getStatus());
    }
}
