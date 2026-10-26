package vn.edu.phenikaa.canteen.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.AssignMemberRequest;
import vn.edu.phenikaa.canteen.dto.MemberResponse;
import vn.edu.phenikaa.canteen.dto.RegisterVendorRequest;
import vn.edu.phenikaa.canteen.dto.UpdateVendorRequest;
import vn.edu.phenikaa.canteen.dto.VendorResponse;
import vn.edu.phenikaa.canteen.service.VendorMemberService;
import vn.edu.phenikaa.canteen.service.VendorService;

/** Quản lý quầy (vai trò VENDOR_MANAGER). Mỗi thao tác trên một quầy đều qua VendorAccessChecker (BR03). */
@Tag(name = "Manager - Vendor")
@RestController
@RequestMapping("/api/manager/vendors")
@RequiredArgsConstructor
public class ManagerVendorController {

    private final VendorService vendorService;
    private final VendorMemberService memberService;

    @GetMapping
    public List<VendorResponse> mine() {
        return vendorService.listMine(CurrentUser.id());
    }

    @PostMapping
    public ResponseEntity<VendorResponse> register(@Valid @RequestBody RegisterVendorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vendorService.register(CurrentUser.id(), req));
    }

    @GetMapping("/{vendorId}")
    public VendorResponse get(@PathVariable Long vendorId) {
        return vendorService.getForManager(vendorId);
    }

    @PutMapping("/{vendorId}")
    public VendorResponse update(@PathVariable Long vendorId, @Valid @RequestBody UpdateVendorRequest req) {
        return vendorService.update(vendorId, req);
    }

    @GetMapping("/{vendorId}/members")
    public List<MemberResponse> members(@PathVariable Long vendorId) {
        return memberService.listForManager(vendorId);
    }

    @PostMapping("/{vendorId}/members")
    public ResponseEntity<MemberResponse> assign(@PathVariable Long vendorId,
                                                 @Valid @RequestBody AssignMemberRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.assignAsManager(vendorId, req));
    }

    @DeleteMapping("/{vendorId}/members/{memberId}")
    public ResponseEntity<Void> remove(@PathVariable Long vendorId, @PathVariable Long memberId) {
        memberService.removeAsManager(vendorId, memberId);
        return ResponseEntity.noContent().build();
    }
}
