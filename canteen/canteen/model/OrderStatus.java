package vn.edu.phenikaa.canteen.model;

/** Bảng chuyển trạng thái hợp lệ nằm ở {@code OrderStateMachine}, không nằm ở đây. */
public enum OrderStatus {
    PENDING, CONFIRMED, PREPARING, READY, COMPLETED, CANCELLED, EXPIRED, NOT_COLLECTED
}
