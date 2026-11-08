package vn.edu.phenikaa.canteen.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.AssignMemberRequest;
import vn.edu.phenikaa.canteen.dto.MemberResponse;
import vn.edu.phenikaa.canteen.dto.SuspendVendorRequest;
import vn.edu.phenikaa.canteen.dto.VendorResponse;
import vn.edu.phenikaa.canteen.model.VendorStatus;
import vn.edu.phenikaa.canteen.service.VendorMemberService;
import vn.edu.phenikaa.canteen.service.VendorService;

/** Quản trị: duyệt, tạm ngừng, mở lại quầy và phân công nhân sự. Admin không vận hành quầy nên không qua VendorAccessChecker. */
@Tag(name = "Admin - Vendor")
@RestController
@RequestMapping("/api/admin/vendors")
@RequiredArgsConstructor
public class AdminVendorController {

    private final VendorService vendorService;
    private final VendorMemberService memberService;

    @GetMapping
    public PageResponse<VendorResponse> search(@RequestParam(required = false) VendorStatus status,
                                               @RequestParam(required = false) Long areaId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return vendorService.search(status, areaId, PageParams.of(page, size));
    }

    @GetMapping("/{vendorId}")
    public VendorResponse get(@PathVariable Long vendorId) {
        return vendorService.getForAdmin(vendorId);
    }

    @PostMapping("/{vendorId}/approve")
    public VendorResponse approve(@PathVariable Long vendorId) {
        return vendorService.approve(CurrentUser.id(), vendorId);
    }

    @PostMapping("/{vendorId}/suspend")
    public VendorResponse suspend(@PathVariable Long vendorId, @Valid @RequestBody SuspendVendorRequest req) {
        return vendorService.suspend(vendorId, req.reason());
    }

    @PostMapping("/{vendorId}/reactivate")
    public VendorResponse reactivate(@PathVariable Long vendorId) {
        return vendorService.reactivate(vendorId);
    }

    @GetMapping("/{vendorId}/members")
    public List<MemberResponse> members(@PathVariable Long vendorId) {
        return memberService.listForAdmin(vendorId);
    }

    @PostMapping("/{vendorId}/members")
    public ResponseEntity<MemberResponse> assign(@PathVariable Long vendorId,
                                                 @Valid @RequestBody AssignMemberRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.assignAsAdmin(vendorId, req));
    }

    @DeleteMapping("/{vendorId}/members/{memberId}")
    public ResponseEntity<Void> remove(@PathVariable Long vendorId, @PathVariable Long memberId) {
        memberService.removeAsAdmin(vendorId, memberId);
        return ResponseEntity.noContent().build();
    }
}
