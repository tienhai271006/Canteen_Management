package vn.edu.phenikaa.canteen.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.service.FirstOrderDiscountProvider.Terms;

/**
 * Tính tiền và khoản giảm (UC5.3, UC8.3). Kết quả được lưu cố định vào đơn (BR04), nên đây là hàm thuần:
 * không ghi gì, không giữ quyền ưu đãi.
 * <p>
 * Công thức giảm: nếu tạm tính &lt; mức tối thiểu thì 0; ngược lại giảm = min(tạm tính × phần trăm / 100,
 * mức trần, tạm tính). Phần trăm làm tròn xuống đồng.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final ObjectProvider<FirstOrderDiscountProvider> discountProvider;

    public record LineInput(Long menuItemId, Long dishId, String dishName, long unitPrice, int quantity) {
    }

    public record PricedLine(Long menuItemId, Long dishId, String dishName, long unitPrice, int quantity,
                             long lineTotal) {
    }

    /** {@code entitlementId} khác null khi có khoản giảm cần giữ quyền cho đơn. */
    public record Quote(List<PricedLine> lines, long subtotal, long discount, long total, Long entitlementId) {
    }

    public Quote quote(Long customerId, List<LineInput> inputs) {
        List<PricedLine> lines = new ArrayList<>(inputs.size());
        long subtotal = 0;
        for (LineInput in : inputs) {
            long lineTotal = Math.multiplyExact(in.unitPrice(), (long) in.quantity());
            lines.add(new PricedLine(in.menuItemId(), in.dishId(), in.dishName(), in.unitPrice(),
                    in.quantity(), lineTotal));
            subtotal = Math.addExact(subtotal, lineTotal);
        }

        long discount = 0;
        Long entitlementId = null;
        FirstOrderDiscountProvider provider = discountProvider.getIfAvailable();
        if (provider != null) {
            Optional<Terms> terms = provider.findUsable(customerId);
            if (terms.isPresent()) {
                discount = discountFor(subtotal, terms.get());
                if (discount > 0) {
                    entitlementId = terms.get().entitlementId();
                }
            }
        }
        return new Quote(List.copyOf(lines), subtotal, discount, subtotal - discount, entitlementId);
    }

    public static long discountFor(long subtotal, Terms terms) {
        if (subtotal <= 0 || subtotal < terms.minOrderAmount()) {
            return 0;
        }
        long byPercent = Math.multiplyExact(subtotal, (long) terms.percent()) / 100;
        return Math.min(Math.min(byPercent, terms.maxDiscountAmount()), subtotal);
    }
}
