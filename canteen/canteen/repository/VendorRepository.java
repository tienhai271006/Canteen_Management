package vn.edu.phenikaa.canteen.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Vendor;
import vn.edu.phenikaa.canteen.model.VendorStatus;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    @Query("select v from Vendor v join fetch v.area where v.id = :id")
    Optional<Vendor> findWithAreaById(@Param("id") Long id);

    @Query(value = "select v from Vendor v join fetch v.area a "
            + "where (:status is null or v.status = :status) and (:areaId is null or a.id = :areaId) "
            + "order by v.id desc",
            countQuery = "select count(v) from Vendor v "
                    + "where (:status is null or v.status = :status) and (:areaId is null or v.area.id = :areaId)")
    Page<Vendor> search(@Param("status") VendorStatus status, @Param("areaId") Long areaId, Pageable pageable);

    /** Các quầy mà người dùng đang là thành viên active. */
    @Query("select v from VendorMember m join m.vendor v join fetch v.area "
            + "where m.user.id = :userId and m.active = true order by v.id")
    List<Vendor> findByActiveMember(@Param("userId") Long userId);

    /** Danh sách công khai: chỉ quầy đang hoạt động. */
    @Query("select v from Vendor v join fetch v.area a "
            + "where v.status = :status and (:areaId is null or a.id = :areaId) order by a.name, v.name")
    List<Vendor> findPublic(@Param("status") VendorStatus status, @Param("areaId") Long areaId);

    boolean existsByAreaIdAndNameIgnoreCase(Long areaId, String name);

    boolean existsByAreaIdAndNameIgnoreCaseAndIdNot(Long areaId, String name, Long id);
}
