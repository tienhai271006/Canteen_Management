package vn.edu.phenikaa.canteen.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;
import vn.edu.phenikaa.canteen.model.UserAccount;

/**
 * Nguồn sự thật cho NFR01/BR03. Không xóa dòng khi gỡ nhân sự mà đặt {@code active = false}
 * để còn dấu vết; VendorAccessChecker chỉ tính các dòng active.
 */
@Getter
@Setter
@Entity
@Table(name = "vendor_member")
public class VendorMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 20)
    private MemberRole memberRole;

    @Column(nullable = false)
    private boolean active = true;
}
