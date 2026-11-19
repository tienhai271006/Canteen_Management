package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Một lần nền tảng chuyển khoản bù cho quầy; {@code forDate} là ngày phục vụ mà khoản này bù cho. */
public record ReimbursementRequest(
        @NotNull Long vendorId,
        @NotNull LocalDate forDate,
        @Positive @Max(1_000_000_000L) long amount,
        @Size(max = 255) String note) {
}
