package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import vn.edu.phenikaa.canteen.model.Quota;

public interface QuotaRepository extends JpaRepository<Quota, Long>, JpaSpecificationExecutor<Quota> {

    Optional<Quota> findByMenuItemId(Long menuItemId);

    List<Quota> findByMenuItemIdIn(Collection<Long> menuItemIds);

    /** SELECT ... FOR UPDATE: xếp hàng cùng với câu UPDATE giữ suất của Pha 6, nên so sánh với reserved luôn đúng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quota q where q.menuItem.id = :menuItemId")
    Optional<Quota> findByMenuItemIdForUpdate(@Param("menuItemId") Long menuItemId);

    /** Tổng suất đã cam kết trong một thực đơn. */
    @Query("select coalesce(sum(q.reservedSlots), 0) from Quota q where q.menuItem.menu.id = :menuId")
    long sumReservedByMenuId(@Param("menuId") Long menuId);

    /**
     * Giữ suất bằng MỘT câu UPDATE có điều kiện (BR12, NFR02): chỉ thành công khi còn đủ suất online,
     * và InnoDB khóa dòng nên hai request tranh suất cuối thì chỉ một bên được 1 dòng bị ảnh hưởng.
     * Phải tăng version để khóa lạc quan của QuotaAdminService vẫn đúng.
     *
     * @return 1 nếu giữ được, 0 nếu không đủ suất
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE quota SET reserved_slots = reserved_slots + :qty, version = version + 1, updated_at = :now "
            + "WHERE menu_item_id = :menuItemId AND total_slots - counter_slots - reserved_slots >= :qty",
            nativeQuery = true)
    int tryReserve(@Param("menuItemId") Long menuItemId, @Param("qty") int qty, @Param("now") LocalDateTime now);

    /** Trả suất. Chỉ trừ khi đang giữ đủ, nên không bao giờ làm reserved âm. @return 1 nếu trả được, 0 nếu không */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE quota SET reserved_slots = reserved_slots - :qty, version = version + 1, updated_at = :now "
            + "WHERE menu_item_id = :menuItemId AND reserved_slots >= :qty",
            nativeQuery = true)
    int tryRelease(@Param("menuItemId") Long menuItemId, @Param("qty") int qty, @Param("now") LocalDateTime now);
}
