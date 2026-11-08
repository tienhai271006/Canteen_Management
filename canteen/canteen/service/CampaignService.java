package vn.edu.phenikaa.canteen.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.dto.CampaignRequest;
import vn.edu.phenikaa.canteen.dto.CampaignResponse;
import vn.edu.phenikaa.canteen.model.Campaign;
import vn.edu.phenikaa.canteen.repository.CampaignRepository;

/**
 * UC8.1 (phần quản trị viên): quản lý chiến dịch. Không xóa chiến dịch vì quyền đã cấp còn tham chiếu tới nó;
 * muốn dừng thì tắt (deactivate). Cùng lúc chỉ có một chiến dịch hiệu lực để BR06 "một lượt toàn nền tảng" rõ nghĩa.
 * Sửa điều khoản chỉ ảnh hưởng đơn tạo SAU đó, đơn cũ đã chốt giá (BR04).
 */
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaigns;

    @Transactional(readOnly = true)
    public PageResponse<CampaignResponse> list(int page, int size) {
        Pageable base = PageParams.of(page, size);
        Pageable newestFirst = PageRequest.of(base.getPageNumber(), base.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(campaigns.findAll(newestFirst), CampaignResponse::from);
    }

    @Transactional(readOnly = true)
    public CampaignResponse get(Long id) {
        return CampaignResponse.from(load(id));
    }

    @Transactional
    public CampaignResponse create(CampaignRequest req) {
        validate(req);
        requireNoOverlap(-1L, req);
        Campaign c = new Campaign();
        apply(c, req);
        c.setActive(true);
        return CampaignResponse.from(campaigns.save(c));
    }

    @Transactional
    public CampaignResponse update(Long id, CampaignRequest req) {
        validate(req);
        Campaign c = load(id);
        if (c.isActive()) {
            requireNoOverlap(id, req);
        }
        apply(c, req);
        return CampaignResponse.from(c);
    }

    @Transactional
    public CampaignResponse setActive(Long id, boolean active) {
        Campaign c = load(id);
        if (active && !c.isActive()) {
            requireNoOverlap(id, c.getStartsAt(), c.getEndsAt());
        }
        c.setActive(active);
        return CampaignResponse.from(c);
    }

    private Campaign load(Long id) {
        return campaigns.findById(id).orElseThrow(() -> BusinessException.notFound("Không tìm thấy chiến dịch."));
    }

    private static void validate(CampaignRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thời điểm kết thúc phải sau thời điểm bắt đầu.");
        }
    }

    private void requireNoOverlap(Long excludeId, CampaignRequest req) {
        requireNoOverlap(excludeId, req.startsAt(), req.endsAt());
    }

    private void requireNoOverlap(Long excludeId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (campaigns.existsActiveOverlap(excludeId, start, end)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_OVERLAP);
        }
    }

    private static void apply(Campaign c, CampaignRequest req) {
        c.setName(req.name().trim());
        c.setDiscountPercent(req.discountPercent());
        c.setMaxDiscountAmount(req.maxDiscountAmount());
        c.setMinOrderAmount(req.minOrderAmount());
        c.setEntitlementValidDays(req.entitlementValidDays());
        c.setStartsAt(req.startsAt());
        c.setEndsAt(req.endsAt());
    }
}
