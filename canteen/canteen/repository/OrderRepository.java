package vn.edu.phenikaa.canteen.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    @Query("select o from CustomerOrder o "
            + "join fetch o.vendor "
            + "join fetch o.pickupSlot ps "
            + "join fetch ps.menu "
            + "left join fetch o.items "
            + "where o.id = :id")
    Optional<Order> findDetailById(@Param("id") Long id);

    /** Đơn của một khách, mới nhất trước (UC6.1). Chỉ fetch quan hệ N-1 nên phân trang ngay ở DB. */
    @Query(value = "select o from CustomerOrder o "
            + "join fetch o.vendor join fetch o.pickupSlot ps join fetch ps.menu "
            + "where o.customerId = :customerId and o.status in :statuses "
            + "order by o.createdAt desc, o.id desc",
            countQuery = "select count(o) from CustomerOrder o "
                    + "where o.customerId = :customerId and o.status in :statuses")
    Page<Order> findMine(@Param("customerId") Long customerId,
                         @Param("statuses") Collection<OrderStatus> statuses,
                         Pageable pageable);

    /** Hàng đợi của quầy, đơn cũ nhất trước (UC7). */
    @Query(value = "select o from CustomerOrder o "
            + "join fetch o.vendor join fetch o.pickupSlot ps join fetch ps.menu "
            + "where o.vendor.id = :vendorId and o.status in :statuses "
            + "order by o.createdAt asc, o.id asc",
            countQuery = "select count(o) from CustomerOrder o "
                    + "where o.vendor.id = :vendorId and o.status in :statuses")
    Page<Order> findByVendorAndStatuses(@Param("vendorId") Long vendorId,
                                        @Param("statuses") Collection<OrderStatus> statuses,
                                        Pageable pageable);

    /** Tìm theo mã đơn khách đọc ở quầy (UC7.4), luôn kèm điều kiện đúng quầy. */
    @Query("select o from CustomerOrder o "
            + "join fetch o.vendor join fetch o.pickupSlot ps join fetch ps.menu "
            + "left join fetch o.items "
            + "where o.vendor.id = :vendorId and o.orderCode = :code")
    Optional<Order> findDetailByVendorAndCode(@Param("vendorId") Long vendorId, @Param("code") String code);

    /** Đơn Pending đã quá hạn xác nhận, cũ nhất trước (BR13). Chỉ lấy id; job tự nạp lại từng đơn trong transaction riêng. */
    @Query("select o.id from CustomerOrder o where o.status = :pending and o.pendingExpiresAt <= :now "
            + "order by o.pendingExpiresAt, o.id")
    java.util.List<Long> findExpiredPendingIds(@Param("pending") OrderStatus pending,
                                               @Param("now") java.time.LocalDateTime now, Pageable pageable);

    /** UC7.8: đơn Ready đã quá hạn nhận hiện hành, cần quầy kiểm tra thực tế. Đơn có hạn cũ nhất lên đầu. */
    @Query(value = "select o from CustomerOrder o join fetch o.vendor join fetch o.pickupSlot ps join fetch ps.menu "
            + "where o.vendor.id = :vendorId and o.status = :ready and o.pickupDeadline is not null "
            + "and o.pickupDeadline <= :now order by o.pickupDeadline asc, o.id asc",
            countQuery = "select count(o) from CustomerOrder o where o.vendor.id = :vendorId and o.status = :ready "
                    + "and o.pickupDeadline is not null and o.pickupDeadline <= :now")
    Page<Order> findOverdueReady(@Param("vendorId") Long vendorId, @Param("ready") OrderStatus ready,
                                 @Param("now") java.time.LocalDateTime now, Pageable pageable);

    /** SELECT ... FOR UPDATE: hai nhân viên xử lý món của cùng một đơn thì xếp hàng, tổng đã xử lý luôn đúng (UC3.7). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
