package vn.edu.phenikaa.canteen.model;

/** Ai thực hiện một lần đổi trạng thái. SYSTEM là job chạy nền. Khớp cột actor_type của order_status_history. */
public enum ActorType {
    CUSTOMER, STAFF, MANAGER, ADMIN, SYSTEM
}
