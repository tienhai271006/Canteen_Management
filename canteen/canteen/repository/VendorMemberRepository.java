package vn.edu.phenikaa.canteen.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.MemberRole;
import vn.edu.phenikaa.canteen.model.VendorMember;

public interface VendorMemberRepository extends JpaRepository<VendorMember, Long> {

    Optional<VendorMember> findByUserIdAndVendorId(Long userId, Long vendorId);

    Optional<VendorMember> findByIdAndVendorId(Long id, Long vendorId);

    @Query("select m from VendorMember m join fetch m.user "
            + "where m.vendor.id = :vendorId and m.active = true order by m.id")
    List<VendorMember> findActiveByVendorId(@Param("vendorId") Long vendorId);

    long countByVendorIdAndMemberRoleAndActiveTrue(Long vendorId, MemberRole memberRole);
}
