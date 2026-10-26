package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.Vendor;
import vn.edu.phenikaa.canteen.model.VendorStatus;

/** Dạng đầy đủ cho quản lý và admin. Phải gọi trong transaction vì {@code area} nạp lười. */
public record VendorResponse(
        Long id,
        Long areaId,
        String areaName,
        String name,
        String description,
        String pickupLocation,
        VendorStatus status,
        String statusReason,
        LocalDateTime approvedAt,
        LocalDateTime createdAt) {

    public static VendorResponse from(Vendor v) {
        return new VendorResponse(v.getId(), v.getArea().getId(), v.getArea().getName(), v.getName(),
                v.getDescription(), v.getPickupLocation(), v.getStatus(), v.getStatusReason(),
                v.getApprovedAt(), v.getCreatedAt());
    }
}
