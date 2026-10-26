package vn.edu.phenikaa.canteen.common.security;

import vn.edu.phenikaa.canteen.model.Role;

/** Principal đặt vào SecurityContext. Role lấy từ DB mỗi request nên đổi quyền có hiệu lực ngay. */
public record AuthenticatedUser(Long id, String email, Role role) {
}
