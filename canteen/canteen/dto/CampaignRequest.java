package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cấu hình chiến dịch (UC8.1). Mặc định của đề tài: 50%, tối đa 30.000đ, đơn tối thiểu 25.000đ, hạn 7 ngày. */
public record CampaignRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(1) @Max(100) Integer discountPercent,
        @NotNull @Min(0) Long maxDiscountAmount,
        @NotNull @Min(0) Long minOrderAmount,
        @NotNull @Min(1) @Max(365) Integer entitlementValidDays,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt) {
}
