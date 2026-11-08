package vn.edu.phenikaa.canteen.dto;

import vn.edu.phenikaa.canteen.model.Role;
import vn.edu.phenikaa.canteen.model.UserAccount;

public record ProfileResponse(
        Long id,
        String fullName,
        String email,
        String pendingEmail,
        boolean emailVerified,
        String studentCode,
        String phone,
        Role role) {

    public static ProfileResponse from(UserAccount u) {
        return new ProfileResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPendingEmail(),
                u.isEmailVerified(), u.getStudentCode(), u.getPhone(), u.getRole());
    }
}
