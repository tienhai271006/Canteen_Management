package vn.edu.phenikaa.canteen.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.PromotionOverviewResponse;
import vn.edu.phenikaa.canteen.service.PromotionService;

/** UC8.2: khách xem điều kiện, hạn dùng và trạng thái quyền ưu đãi của chính mình (BR03). */
@Tag(name = "Customer - Promotion")
@RestController
@RequestMapping("/api/me/promotion")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class MyPromotionController {

    private final PromotionService promotions;

    @GetMapping
    public PromotionOverviewResponse overview() {
        return promotions.overview(CurrentUser.id());
    }
}
