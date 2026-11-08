package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.ActorType;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.model.OrderStatusHistory;

/** Một dòng tiến độ của đơn (UC6.1). Chỉ lộ loại người thực hiện, không lộ id nhân viên (NFR03, BR03). */
public record OrderHistoryResponse(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        ActorType actorType,
        String reason,
        LocalDateTime createdAt) {

    public static OrderHistoryResponse from(OrderStatusHistory h) {
        return new OrderHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getActorType(), h.getReason(),
                h.getCreatedAt());
    }
}
