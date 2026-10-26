package vn.edu.phenikaa.canteen.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.OrderProperties;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.AddCartItemRequest;
import vn.edu.phenikaa.canteen.dto.CartResponse;
import vn.edu.phenikaa.canteen.dto.UpdateCartItemRequest;
import vn.edu.phenikaa.canteen.model.Cart;
import vn.edu.phenikaa.canteen.model.CartItem;
import vn.edu.phenikaa.canteen.model.Dish;
import vn.edu.phenikaa.canteen.model.Menu;
import vn.edu.phenikaa.canteen.model.MenuItem;
import vn.edu.phenikaa.canteen.model.Quota;
import vn.edu.phenikaa.canteen.repository.CartRepository;
import vn.edu.phenikaa.canteen.repository.MenuItemRepository;
import vn.edu.phenikaa.canteen.repository.QuotaRepository;
import vn.edu.phenikaa.canteen.service.PricingService.LineInput;
import vn.edu.phenikaa.canteen.service.PricingService.Quote;

/**
 * UC5: giỏ hàng. Giỏ chỉ chứa món của một quầy (BR01) và KHÔNG giữ suất (BR11): thêm món không trừ hạn mức,
 * nên giỏ có thể chứa món sau đó hết suất. Việc đó chỉ được kiểm tra thật khi đặt đơn.
 * <p>
 * Ngoài quầy, giỏ còn chỉ chứa món của cùng một thực đơn (cùng ngày và buổi), vì một đơn chỉ có một khung giờ nhận
 * mà khung giờ thuộc về một thực đơn.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository carts;
    private final MenuItemRepository menuItems;
    private final QuotaRepository quotas;
    private final OrderabilityChecker orderability;
    private final PricingService pricing;
    private final OrderProperties props;

    @Transactional(readOnly = true)
    public CartResponse get() {
        Long userId = CurrentUser.id();
        return carts.findDetailByUserId(userId).map(c -> toResponse(userId, c)).orElseGet(CartResponse::empty);
    }

    @Transactional
    public CartResponse addItem(AddCartItemRequest req) {
        Long userId = CurrentUser.id();
        MenuItem item = menuItems.findDetailById(req.menuItemId())
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy món trong thực đơn."));
        orderability.require(item);

        Cart cart = carts.findDetailByUserId(userId).orElseGet(() -> newCart(userId));
        requireSameVendorAndMenu(cart, item);

        CartItem line = cart.findItem(item.getId()).orElse(null);
        int newQty = (line == null ? 0 : line.getQuantity()) + req.quantity();
        requireValidQuantity(newQty);

        if (line == null) {
            line = new CartItem();
            line.setMenuItem(item);
            cart.addItem(line);
        }
        line.setQuantity(newQty);
        if (req.note() != null) {
            line.setNote(blankToNull(req.note()));
        }
        cart.setVendorId(item.getMenu().getVendor().getId());
        carts.saveAndFlush(cart);
        return toResponse(userId, cart);
    }

    @Transactional
    public CartResponse updateItem(Long menuItemId, UpdateCartItemRequest req) {
        Long userId = CurrentUser.id();
        Cart cart = requireCart(userId);
        CartItem line = cart.findItem(menuItemId)
                .orElseThrow(() -> BusinessException.notFound("Món không có trong giỏ."));
        requireValidQuantity(req.quantity());
        line.setQuantity(req.quantity());
        line.setNote(blankToNull(req.note()));
        carts.saveAndFlush(cart);
        return toResponse(userId, cart);
    }

    @Transactional
    public CartResponse removeItem(Long menuItemId) {
        Long userId = CurrentUser.id();
        Cart cart = requireCart(userId);
        CartItem line = cart.findItem(menuItemId)
                .orElseThrow(() -> BusinessException.notFound("Món không có trong giỏ."));
        cart.getItems().remove(line);
        if (cart.getItems().isEmpty()) {
            cart.setVendorId(null);
        }
        carts.saveAndFlush(cart);
        return toResponse(userId, cart);
    }

    @Transactional
    public void clear() {
        carts.findDetailByUserId(CurrentUser.id()).ifPresent(cart -> {
            cart.empty();
            carts.saveAndFlush(cart);
        });
    }

    // ---- nội bộ ----

    private Cart newCart(Long userId) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        return cart;
    }

    private Cart requireCart(Long userId) {
        return carts.findDetailByUserId(userId).orElseThrow(() -> BusinessException.notFound("Giỏ hàng đang trống."));
    }

    private void requireSameVendorAndMenu(Cart cart, MenuItem item) {
        if (cart.getItems().isEmpty()) {
            return;
        }
        Menu menu = item.getMenu();
        if (cart.getVendorId() != null && !cart.getVendorId().equals(menu.getVendor().getId())) {
            throw new BusinessException(ErrorCode.CART_MULTI_VENDOR);
        }
        Long cartMenuId = cart.getItems().get(0).getMenuItem().getMenu().getId();
        if (!cartMenuId.equals(menu.getId())) {
            throw new BusinessException(ErrorCode.CART_MULTI_MENU);
        }
    }

    private void requireValidQuantity(int qty) {
        if (qty < 1 || qty > props.maxQuantityPerItem()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Số lượng mỗi món phải từ 1 đến " + props.maxQuantityPerItem() + ".");
        }
    }

    private CartResponse toResponse(Long userId, Cart cart) {
        if (cart.getItems().isEmpty()) {
            return CartResponse.empty();
        }
        List<CartItem> lines = cart.getItems();
        Map<Long, Quota> quotaByItem = quotas
                .findByMenuItemIdIn(lines.stream().map(l -> l.getMenuItem().getId()).toList()).stream()
                .collect(Collectors.toMap(q -> q.getMenuItem().getId(), Function.identity()));

        List<LineInput> inputs = lines.stream().map(l -> {
            Dish d = l.getMenuItem().getDish();
            return new LineInput(l.getMenuItem().getId(), d.getId(), d.getName(), d.getPrice(), l.getQuantity());
        }).toList();
        Quote quote = pricing.quote(userId, inputs);

        List<CartResponse.Line> out = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            CartItem l = lines.get(i);
            MenuItem mi = l.getMenuItem();
            var priced = quote.lines().get(i);
            Quota q = quotaByItem.get(mi.getId());
            int available = q == null ? 0 : Math.max(q.availableOnline(), 0);
            ErrorCode problem = orderability.problemOf(mi);
            if (problem == null && l.getQuantity() > available) {
                problem = ErrorCode.QUOTA_EXCEEDED;
            }
            out.add(new CartResponse.Line(mi.getId(), priced.dishId(), priced.dishName(), priced.unitPrice(),
                    priced.quantity(), priced.lineTotal(), l.getNote(), available,
                    problem == null ? null : problem.name()));
        }

        Menu menu = lines.get(0).getMenuItem().getMenu();
        return new CartResponse(menu.getVendor().getId(), menu.getVendor().getName(), menu.getId(),
                menu.getMenuDate(), menu.getMealSession(), menu.getOrderCutoffTime(), out,
                quote.subtotal(), quote.discount(), quote.total());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
