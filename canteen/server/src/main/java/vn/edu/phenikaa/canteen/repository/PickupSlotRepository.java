package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.PickupSlot;

public interface PickupSlotRepository extends JpaRepository<PickupSlot, Long> {

    List<PickupSlot> findByMenuIdOrderByStartTime(Long menuId);

    List<PickupSlot> findByMenuIdInOrderByStartTime(Collection<Long> menuIds);

    Optional<PickupSlot> findByIdAndMenuId(Long id, Long menuId);

    long countByMenuId(Long menuId);

    /** Khung nhận kèm thực đơn và quầy, dùng khi đặt đơn (Pha 6). */
    /** Giữ suất cho khung. Một câu UPDATE có điều kiện, không đọc rồi ghi. @return 1 nếu giữ được, 0 nếu khung không đủ chỗ */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE pickup_slot SET reserved_qty = reserved_qty + :qty, version = version + 1, updated_at = :now "
            + "WHERE id = :id AND (capacity IS NULL OR capacity - reserved_qty >= :qty)", nativeQuery = true)
    int tryReserve(@Param("id") Long id, @Param("qty") int qty, @Param("now") LocalDateTime now);

    /** Trả suất. Chỉ trừ khi đang giữ đủ nên không bao giờ làm reserved_qty âm. @return 1 nếu trả được */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE pickup_slot SET reserved_qty = reserved_qty - :qty, version = version + 1, updated_at = :now "
            + "WHERE id = :id AND reserved_qty >= :qty", nativeQuery = true)
    int tryRelease(@Param("id") Long id, @Param("qty") int qty, @Param("now") LocalDateTime now);

    /** Đặt hạn mức, không cho thấp hơn số suất đang giữ. @return 0 nếu thấp hơn số đang giữ (hoặc không có khung) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE pickup_slot SET capacity = :capacity, version = version + 1, updated_at = :now "
            + "WHERE id = :id AND reserved_qty <= :capacity", nativeQuery = true)
    int setCapacity(@Param("id") Long id, @Param("capacity") int capacity, @Param("now") LocalDateTime now);

    /** Bỏ giới hạn. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE pickup_slot SET capacity = NULL, version = version + 1, updated_at = :now WHERE id = :id",
            nativeQuery = true)
    int clearCapacity(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Query("select s from PickupSlot s join fetch s.menu m join fetch m.vendor where s.id = :id")
    Optional<PickupSlot> findDetailById(@Param("id") Long id);
}
