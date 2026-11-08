package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.model.Menu;
import vn.edu.phenikaa.canteen.repository.MenuRepository;

/**
 * Cửa kiểm tra chung cho mọi thao tác trên thực đơn. vendorId luôn lấy từ bản ghi thực đơn trong DB,
 * không lấy từ URL, nên đổi id trên URL sang thực đơn của quầy khác sẽ bị từ chối (NFR01, BR03).
 */
@Component
@RequiredArgsConstructor
public class MenuGuard {

    private final MenuRepository menus;
    private final VendorAccessChecker access;
    private final Clock clock;

    /** Thực đơn thuộc quầy mà người dùng hiện tại là quản lý. */
    public Menu managed(Long menuId) {
        Menu menu = menus.findById(menuId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy thực đơn."));
        access.requireManager(menu.getVendor().getId());
        return menu;
    }

    /** Như {@link #managed}, và còn phải là thực đơn của hôm nay trở đi. */
    public Menu editable(Long menuId) {
        Menu menu = managed(menuId);
        if (menu.getMenuDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.MENU_NOT_EDITABLE);
        }
        return menu;
    }
}
