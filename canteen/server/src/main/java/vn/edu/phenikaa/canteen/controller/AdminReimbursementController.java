package vn.edu.phenikaa.canteen.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.dto.ReimbursementRequest;
import vn.edu.phenikaa.canteen.service.ReimbursementService;

/** Quản trị viên ghi nhận khoản nền tảng đã bù ưu đãi cho quầy; quầy thấy kết quả trong thống kê (UC9.2). */
@Tag(name = "Admin - Reimbursement")
@RestController
@RequestMapping("/api/admin/reimbursements")
@RequiredArgsConstructor
public class AdminReimbursementController {

    private final ReimbursementService reimbursements;

    @PostMapping
    public ResponseEntity<Map<String, Long>> record(@Valid @RequestBody ReimbursementRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", reimbursements.record(req)));
    }
}
