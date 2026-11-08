package vn.edu.phenikaa.canteen.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.dto.VendorStatsResponse;
import vn.edu.phenikaa.canteen.model.MealSession;
import vn.edu.phenikaa.canteen.service.StatsService;

/** UC9: thống kê quầy cho quản lý. Quầy được kiểm tra từ dữ liệu phân công, không chỉ dựa vào vai trò (NFR01, BR03). */
@Tag(name = "Manager - Stats")
@RestController
@RequestMapping("/api/manager/stats")
@RequiredArgsConstructor
public class ManagerStatsController {

    private final StatsService stats;

    /** {@code from}, {@code to} là NGÀY PHỤC VỤ (yyyy-MM-dd), bỏ trống thì 7 ngày gần nhất. {@code session} lọc theo buổi. */
    @GetMapping
    public VendorStatsResponse stats(@RequestParam Long vendorId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) MealSession session) {
        return stats.vendorStats(vendorId, from, to, session);
    }
}
