package vn.edu.phenikaa.canteen.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.dto.CampaignRequest;
import vn.edu.phenikaa.canteen.dto.CampaignResponse;
import vn.edu.phenikaa.canteen.service.CampaignService;

/** UC8.1: quản trị viên quản lý chiến dịch. SecurityConfig giới hạn /api/admin/** cho ADMIN. */
@Tag(name = "Admin - Campaigns")
@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
public class AdminCampaignController {

    private final CampaignService campaigns;

    @GetMapping
    public PageResponse<CampaignResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return campaigns.list(page, size);
    }

    @GetMapping("/{id}")
    public CampaignResponse get(@PathVariable Long id) {
        return campaigns.get(id);
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CampaignRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaigns.create(req));
    }

    @PutMapping("/{id}")
    public CampaignResponse update(@PathVariable Long id, @Valid @RequestBody CampaignRequest req) {
        return campaigns.update(id, req);
    }

    @PostMapping("/{id}/activate")
    public CampaignResponse activate(@PathVariable Long id) {
        return campaigns.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    public CampaignResponse deactivate(@PathVariable Long id) {
        return campaigns.setActive(id, false);
    }
}
