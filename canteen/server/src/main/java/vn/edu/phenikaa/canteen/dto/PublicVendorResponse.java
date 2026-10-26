package vn.edu.phenikaa.canteen.dto;

import vn.edu.phenikaa.canteen.model.Vendor;

/** Dạng công khai: không lộ trạng thái nội bộ, lý do tạm ngừng hay người duyệt. */
public record PublicVendorResponse(
        Long id,
        Long areaId,
        String areaName,
        String name,
        String description,
        String pickupLocation) {

    public static PublicVendorResponse from(Vendor v) {
        return new PublicVendorResponse(v.getId(), v.getArea().getId(), v.getArea().getName(), v.getName(),
                v.getDescription(), v.getPickupLocation());
    }
}
