package vn.edu.phenikaa.canteen.controller;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.storage.FileStorageService;
import vn.edu.phenikaa.canteen.dto.AreaResponse;
import vn.edu.phenikaa.canteen.dto.CatalogCriteria;
import vn.edu.phenikaa.canteen.dto.CatalogItemResponse;
import vn.edu.phenikaa.canteen.dto.PublicVendorResponse;
import vn.edu.phenikaa.canteen.model.DishCategory;
import vn.edu.phenikaa.canteen.model.MealSession;
import vn.edu.phenikaa.canteen.service.CatalogQueryService;
import vn.edu.phenikaa.canteen.service.VendorService;

/** Không cần đăng nhập (SecurityConfig cho phép /api/public/**): khách xem món trước khi đăng ký. */
@Tag(name = "Public - Catalog")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCatalogController {

    private final CatalogQueryService catalog;
    private final VendorService vendorService;
    private final FileStorageService storage;

    @GetMapping("/areas")
    public List<AreaResponse> areas() {
        return vendorService.listAreas();
    }

    @GetMapping("/vendors")
    public List<PublicVendorResponse> vendors(@RequestParam(required = false) Long areaId) {
        return vendorService.listActive(areaId);
    }

    /** Mặc định: món của hôm nay, sắp theo tên. */
    @GetMapping("/menu-items")
    public PageResponse<CatalogItemResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) MealSession session,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DishCategory category,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "NAME") CatalogCriteria.Sort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CatalogCriteria criteria = new CatalogCriteria(date, session, areaId, vendorId, q, category,
                minPrice, maxPrice, availableOnly, sort);
        return catalog.search(criteria, PageParams.of(page, size));
    }

    /** Tên tệp do server sinh (UUID), nên cho trình duyệt giữ bản sao lâu. */
    @GetMapping("/images/{filename}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        Resource resource = storage.load(filename);
        return ResponseEntity.ok()
                .contentType(storage.mediaType(filename))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable())
                .body(resource);
    }
}
