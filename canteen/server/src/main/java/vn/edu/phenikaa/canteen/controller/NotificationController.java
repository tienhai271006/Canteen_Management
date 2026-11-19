package vn.edu.phenikaa.canteen.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.NotificationResponse;
import vn.edu.phenikaa.canteen.service.NotificationService;

/** Thông báo trong ứng dụng của người đang đăng nhập, mọi vai trò (khách, quầy, quản trị). */
@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return notifications.list(CurrentUser.id(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notifications.markRead(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notifications.markAllRead(CurrentUser.id()));
    }
}
