package vn.edu.phenikaa.canteen.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.dto.AreaRequest;
import vn.edu.phenikaa.canteen.dto.AreaResponse;
import vn.edu.phenikaa.canteen.service.VendorService;

/** Thêm hoặc sửa khu vực (D6, Ba Sao...). Xem danh sách khu ở /api/public/areas. */
@Tag(name = "Admin - Area")
@RestController
@RequestMapping("/api/admin/areas")
@RequiredArgsConstructor
public class AdminAreaController {

    private final VendorService vendorService;

    @PostMapping
    public ResponseEntity<AreaResponse> create(@Valid @RequestBody AreaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vendorService.createArea(req));
    }

    @PutMapping("/{areaId}")
    public AreaResponse update(@PathVariable Long areaId, @Valid @RequestBody AreaRequest req) {
        return vendorService.updateArea(areaId, req);
    }
}
