package vn.edu.phenikaa.canteen.service;

import vn.edu.phenikaa.canteen.common.security.AuthenticatedUser;
import vn.edu.phenikaa.canteen.model.ActorType;

/** Người (hoặc hệ thống) thực hiện một thay đổi trạng thái, để ghi vào lịch sử (NFR03). */
public record Actor(Long id, ActorType type) {

    public static Actor customer(Long userId) {
        return new Actor(userId, ActorType.CUSTOMER);
    }

    public static Actor system() {
        return new Actor(null, ActorType.SYSTEM);
    }

    public static Actor of(AuthenticatedUser user) {
        ActorType type = switch (user.role()) {
            case CUSTOMER -> ActorType.CUSTOMER;
            case VENDOR_STAFF -> ActorType.STAFF;
            case VENDOR_MANAGER -> ActorType.MANAGER;
            case ADMIN -> ActorType.ADMIN;
        };
        return new Actor(user.id(), type);
    }
}
