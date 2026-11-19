package vn.edu.phenikaa.canteen.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.dto.AdminOverviewResponse;
import vn.edu.phenikaa.canteen.dto.VendorStatsResponse;
import vn.edu.phenikaa.canteen.model.MealSession;
import vn.edu.phenikaa.canteen.service.StatsService;

/** UC10.7: quản trị viên xem tổng hợp mọi quầy và chi tiết từng quầy. */
@Tag(name = "Admin - Stats")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final StatsService stats;

    @GetMapping("/overview")
    public AdminOverviewResponse overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.adminOverview(from, to);
    }

    @GetMapping("/vendors/{vendorId}")
    public VendorStatsResponse vendor(
            @PathVariable Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) MealSession session) {
        return stats.vendorStats(vendorId, from, to, session);
    }
}
