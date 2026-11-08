package vn.edu.phenikaa.canteen.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.PickupSlot;

public interface PickupSlotRepository extends JpaRepository<PickupSlot, Long> {

    List<PickupSlot> findByMenuIdOrderByStartTime(Long menuId);

    List<PickupSlot> findByMenuIdInOrderByStartTime(Collection<Long> menuIds);

    Optional<PickupSlot> findByIdAndMenuId(Long id, Long menuId);

    long countByMenuId(Long menuId);

    /** Khung nhận kèm thực đơn và quầy, dùng khi đặt đơn (Pha 6). */
    @Query("select s from PickupSlot s join fetch s.menu m join fetch m.vendor where s.id = :id")
    Optional<PickupSlot> findDetailById(@Param("id") Long id);
}
