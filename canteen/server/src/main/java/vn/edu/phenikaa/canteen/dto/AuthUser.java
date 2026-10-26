package vn.edu.phenikaa.canteen.dto;

import java.util.List;

import vn.edu.phenikaa.canteen.model.Role;

/** Thông tin người dùng trả kèm token; vendorIds là các quầy được phân công (UC1.5). */
public record AuthUser(Long id, String email, String fullName, Role role, boolean emailVerified, List<Long> vendorIds) {
}
