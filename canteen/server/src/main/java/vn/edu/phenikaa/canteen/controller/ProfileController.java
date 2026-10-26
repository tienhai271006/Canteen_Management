package vn.edu.phenikaa.canteen.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.AccountStatusResponse;
import vn.edu.phenikaa.canteen.dto.ProfileResponse;
import vn.edu.phenikaa.canteen.dto.UpdateProfileRequest;
import vn.edu.phenikaa.canteen.service.ProfileService;

/** Chỉ thao tác trên hồ sơ của chính người đăng nhập: id lấy từ token, không có id trên URL (BR03). */
@Tag(name = "Profile")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/profile")
    public ProfileResponse profile() {
        return profileService.getProfile(CurrentUser.id());
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return profileService.updateProfile(CurrentUser.id(), req);
    }

    @GetMapping("/account-status")
    public AccountStatusResponse accountStatus() {
        return profileService.getAccountStatus(CurrentUser.id());
    }
}
