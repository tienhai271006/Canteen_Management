package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import vn.edu.phenikaa.canteen.service.FirstOrderDiscountProvider.Terms;
import vn.edu.phenikaa.canteen.service.FirstOrderDiscountProvider;
import vn.edu.phenikaa.canteen.service.PricingService.LineInput;
import vn.edu.phenikaa.canteen.service.PricingService.Quote;
import vn.edu.phenikaa.canteen.service.PricingService;

/**
 * Các ví dụ dưới đây suy ra từ chiến dịch mặc định trong V5/V8 (giảm 50%, trần 30.000đ, đơn tối thiểu 25.000đ),
 * CHƯA phải bảng ví dụ (Bảng 20) của tài liệu đặc tả. Khi có Bảng 20 thì thêm từng dòng vào {@link #discountExamples()}.
 */
class PricingServiceTest {

    private static final Terms TERMS = new Terms(7L, 50, 30_000, 25_000);

    @SuppressWarnings("unchecked")
    private static PricingService serviceWith(FirstOrderDiscountProvider provider) {
        ObjectProvider<FirstOrderDiscountProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(provider);
        return new PricingService(op);
    }

    private static FirstOrderDiscountProvider providerOf(Terms terms) {
        return new FirstOrderDiscountProvider() {
            @Override
            public Optional<Terms> findUsable(Long customerId) {
                return Optional.ofNullable(terms);
            }

            @Override
            public void hold(Long entitlementId, Long orderId) {
            }
        };
    }

    @Test
    void discountExamples() {
        assertThat(PricingService.discountFor(24_999, TERMS)).as("dưới mức tối thiểu").isZero();
        assertThat(PricingService.discountFor(25_000, TERMS)).as("đúng mức tối thiểu").isEqualTo(12_500);
        assertThat(PricingService.discountFor(25_001, TERMS)).as("làm tròn xuống đồng").isEqualTo(12_500);
        assertThat(PricingService.discountFor(50_000, TERMS)).isEqualTo(25_000);
        assertThat(PricingService.discountFor(60_000, TERMS)).as("vừa chạm trần").isEqualTo(30_000);
        assertThat(PricingService.discountFor(100_000, TERMS)).as("bị chặn bởi trần").isEqualTo(30_000);
    }

    @Test
    void discountNeverExceedsSubtotal() {
        Terms generous = new Terms(1L, 100, 1_000_000, 0);
        assertThat(PricingService.discountFor(40_000, generous)).isEqualTo(40_000);
        assertThat(PricingService.discountFor(0, generous)).isZero();
    }

    @Test
    void quoteWithoutAnyProviderHasNoDiscount() {
        Quote q = serviceWith(null).quote(1L, List.of(
                new LineInput(10L, 100L, "Cơm gà", 35_000, 2),
                new LineInput(11L, 101L, "Trà đá", 5_000, 1)));
        assertThat(q.subtotal()).isEqualTo(75_000);
        assertThat(q.discount()).isZero();
        assertThat(q.total()).isEqualTo(75_000);
        assertThat(q.entitlementId()).isNull();
        assertThat(q.lines()).extracting(l -> l.lineTotal()).containsExactly(70_000L, 5_000L);
    }

    @Test
    void quoteAppliesEntitlementAndReportsItForHolding() {
        Quote q = serviceWith(providerOf(TERMS)).quote(1L, List.of(new LineInput(10L, 100L, "Cơm gà", 35_000, 2)));
        assertThat(q.subtotal()).isEqualTo(70_000);
        assertThat(q.discount()).isEqualTo(30_000);
        assertThat(q.total()).isEqualTo(40_000);
        assertThat(q.entitlementId()).isEqualTo(7L);
    }

    @Test
    void orderBelowMinimumDoesNotConsumeEntitlement() {
        Quote q = serviceWith(providerOf(TERMS)).quote(1L, List.of(new LineInput(11L, 101L, "Trà đá", 5_000, 3)));
        assertThat(q.discount()).isZero();
        assertThat(q.total()).isEqualTo(15_000);
        assertThat(q.entitlementId()).as("không đủ điều kiện thì không giữ quyền").isNull();
    }

    @Test
    void customerWithoutEntitlementPaysFullPrice() {
        Quote q = serviceWith(providerOf(null)).quote(1L, List.of(new LineInput(10L, 100L, "Cơm gà", 35_000, 2)));
        assertThat(q.discount()).isZero();
        assertThat(q.entitlementId()).isNull();
    }

    @Test
    void totalIsAlwaysSubtotalMinusDiscount() {
        for (long price : new long[] {1_000, 24_000, 25_000, 59_999, 60_000, 123_456}) {
            Quote q = serviceWith(providerOf(TERMS)).quote(1L, List.of(new LineInput(1L, 1L, "x", price, 1)));
            assertThat(q.total()).isEqualTo(q.subtotal() - q.discount());
            assertThat(q.total()).isGreaterThanOrEqualTo(0);
        }
    }
}
