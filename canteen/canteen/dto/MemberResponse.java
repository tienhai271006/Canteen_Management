package vn.edu.phenikaa.canteen.dto;

import vn.edu.phenikaa.canteen.model.MemberRole;
import vn.edu.phenikaa.canteen.model.VendorMember;

public record MemberResponse(Long id, Long userId, String fullName, String email, MemberRole memberRole) {

    public static MemberResponse from(VendorMember m) {
        return new MemberResponse(m.getId(), m.getUser().getId(), m.getUser().getFullName(),
                m.getUser().getEmail(), m.getMemberRole());
    }
}
