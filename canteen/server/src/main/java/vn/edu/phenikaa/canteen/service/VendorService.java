package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.dto.AreaRequest;
import vn.edu.phenikaa.canteen.dto.AreaResponse;
import vn.edu.phenikaa.canteen.dto.PublicVendorResponse;
import vn.edu.phenikaa.canteen.dto.RegisterVendorRequest;
import vn.edu.phenikaa.canteen.dto.UpdateVendorRequest;
import vn.edu.phenikaa.canteen.dto.VendorResponse;
import vn.edu.phenikaa.canteen.model.Area;
import vn.edu.phenikaa.canteen.model.MemberRole;
import vn.edu.phenikaa.canteen.model.Vendor;
import vn.edu.phenikaa.canteen.model.VendorMember;
import vn.edu.phenikaa.canteen.model.VendorStatus;
import vn.edu.phenikaa.canteen.repository.AreaRepository;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;
import vn.edu.phenikaa.canteen.repository.VendorMemberRepository;
import vn.edu.phenikaa.canteen.repository.VendorRepository;

/**
 * UC2 (R2.1 đến R2.4): đăng ký, duyệt, cập nhật, tạm ngừng.
 * Tạm ngừng chỉ chặn đơn mới: Pha 6 gọi {@link #requireAcceptingOrders(Long)} khi tạo đơn,
 * còn việc xử lý đơn đã có (xác nhận, chuẩn bị, trả món, duyệt hủy) không được kiểm tra trạng thái quầy.
 */
@Service
@RequiredArgsConstructor
public class VendorService {

    private final VendorRepository vendors;
    private final AreaRepository areas;
    private final VendorMemberRepository members;
    private final UserAccountRepository users;
    private final VendorAccessChecker access;
    private final Clock clock;

    // ---------------- Quản lý quầy (manager) ----------------

    /** R2.1: quản lý đăng ký quầy mới. Quầy ở trạng thái PENDING và người đăng ký thành quản lý của quầy đó. */
    @Transactional
    public VendorResponse register(Long userId, RegisterVendorRequest req) {
        Area area = areas.findById(req.areaId())
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy khu vực."));
        String name = req.name().trim();
        if (vendors.existsByAreaIdAndNameIgnoreCase(area.getId(), name)) {
            throw new BusinessException(ErrorCode.VENDOR_NAME_DUPLICATE);
        }

        Vendor vendor = new Vendor();
        vendor.setArea(area);
        vendor.setName(name);
        vendor.setDescription(blankToNull(req.description()));
        vendor.setPickupLocation(blankToNull(req.pickupLocation()));
        vendor.setStatus(VendorStatus.PENDING);
        vendors.save(vendor);

        VendorMember member = new VendorMember();
        member.setUser(users.getReferenceById(userId));
        member.setVendor(vendor);
        member.setMemberRole(MemberRole.MANAGER);
        member.setActive(true);
        members.save(member);

        return VendorResponse.from(vendor);
    }

    @Transactional(readOnly = true)
    public List<VendorResponse> listMine(Long userId) {
        return vendors.findByActiveMember(userId).stream().map(VendorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VendorResponse getForManager(Long vendorId) {
        access.requireManager(vendorId);
        return VendorResponse.from(load(vendorId));
    }

    /** R2.3: cập nhật thông tin. Quầy tạm ngừng vẫn được sửa thông tin. */
    @Transactional
    public VendorResponse update(Long vendorId, UpdateVendorRequest req) {
        access.requireManager(vendorId);
        Vendor vendor = load(vendorId);
        String name = req.name().trim();
        if (vendors.existsByAreaIdAndNameIgnoreCaseAndIdNot(vendor.getArea().getId(), name, vendorId)) {
            throw new BusinessException(ErrorCode.VENDOR_NAME_DUPLICATE);
        }
        vendor.setName(name);
        vendor.setDescription(blankToNull(req.description()));
        vendor.setPickupLocation(blankToNull(req.pickupLocation()));
        return VendorResponse.from(vendor);
    }

    // ---------------- Quản trị (admin) ----------------

    @Transactional(readOnly = true)
    public PageResponse<VendorResponse> search(VendorStatus status, Long areaId, Pageable pageable) {
        return PageResponse.of(vendors.search(status, areaId, pageable), VendorResponse::from);
    }

    @Transactional(readOnly = true)
    public VendorResponse getForAdmin(Long vendorId) {
        return VendorResponse.from(load(vendorId));
    }

    /** R2.2: duyệt quầy. Hai admin duyệt cùng lúc thì một bên nhận 409 nhờ khóa lạc quan. */
    @Transactional
    public VendorResponse approve(Long adminId, Long vendorId) {
        Vendor vendor = load(vendorId);
        vendor.approve(adminId, LocalDateTime.now(clock));
        return VendorResponse.from(vendor);
    }

    /** R2.4: tạm ngừng. Không động tới đơn đang xử lý. */
    @Transactional
    public VendorResponse suspend(Long vendorId, String reason) {
        Vendor vendor = load(vendorId);
        vendor.suspend(reason.trim());
        return VendorResponse.from(vendor);
    }

    @Transactional
    public VendorResponse reactivate(Long vendorId) {
        Vendor vendor = load(vendorId);
        vendor.reactivate();
        return VendorResponse.from(vendor);
    }

    // ---------------- Khu vực ----------------

    @Transactional(readOnly = true)
    public List<AreaResponse> listAreas() {
        return areas.findAllByOrderByNameAsc().stream().map(AreaResponse::from).toList();
    }

    @Transactional
    public AreaResponse createArea(AreaRequest req) {
        String code = req.code().trim();
        if (areas.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã khu vực đã tồn tại.");
        }
        Area area = new Area();
        applyArea(area, req, code);
        return AreaResponse.from(areas.save(area));
    }

    @Transactional
    public AreaResponse updateArea(Long areaId, AreaRequest req) {
        Area area = areas.findById(areaId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy khu vực."));
        String code = req.code().trim();
        if (areas.existsByCodeIgnoreCaseAndIdNot(code, areaId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã khu vực đã tồn tại.");
        }
        applyArea(area, req, code);
        return AreaResponse.from(area);
    }

    // ---------------- Công khai và dùng cho các pha sau ----------------

    /** Quầy đang hoạt động, dùng cho trang chọn quầy của khách. */
    @Transactional(readOnly = true)
    public List<PublicVendorResponse> listActive(Long areaId) {
        return vendors.findPublic(VendorStatus.ACTIVE, areaId).stream().map(PublicVendorResponse::from).toList();
    }

    /** Pha 6 gọi khi tạo đơn mới (BR02). Quầy chờ duyệt hoặc tạm ngừng thì từ chối. */
    @Transactional(readOnly = true)
    public void requireAcceptingOrders(Long vendorId) {
        Vendor vendor = vendors.findById(vendorId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy quầy."));
        if (!vendor.isActive()) {
            throw new BusinessException(ErrorCode.VENDOR_NOT_ACTIVE);
        }
    }

    private Vendor load(Long vendorId) {
        return vendors.findWithAreaById(vendorId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy quầy."));
    }

    private static void applyArea(Area area, AreaRequest req, String code) {
        area.setCode(code);
        area.setName(req.name().trim());
        area.setDescription(blankToNull(req.description()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
