package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edu.phenikaa.canteen.model.MemberRole;

public record AssignMemberRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull MemberRole memberRole) {
}
