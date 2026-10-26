package vn.edu.phenikaa.canteen.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.dto.AddCartItemRequest;
import vn.edu.phenikaa.canteen.dto.CartResponse;
import vn.edu.phenikaa.canteen.dto.UpdateCartItemRequest;
import vn.edu.phenikaa.canteen.service.CartService;

/** Giỏ của chính người gọi, lấy từ token nên không có id người dùng trên URL (BR03). */
@Tag(name = "Customer - Cart")
@RestController
@RequestMapping("/api/cart")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse get() {
        return cartService.get();
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest req) {
        return cartService.addItem(req);
    }

    @PutMapping("/items/{menuItemId}")
    public CartResponse updateItem(@PathVariable Long menuItemId, @Valid @RequestBody UpdateCartItemRequest req) {
        return cartService.updateItem(menuItemId, req);
    }

    @DeleteMapping("/items/{menuItemId}")
    public CartResponse removeItem(@PathVariable Long menuItemId) {
        return cartService.removeItem(menuItemId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        cartService.clear();
        return ResponseEntity.noContent().build();
    }
}
