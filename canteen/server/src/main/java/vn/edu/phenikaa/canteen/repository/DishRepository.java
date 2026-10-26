package vn.edu.phenikaa.canteen.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Dish;

public interface DishRepository extends JpaRepository<Dish, Long> {

    @Query(value = "select d from Dish d where d.vendor.id = :vendorId and (:includeHidden = true or d.active = true) "
            + "order by d.active desc, d.name",
            countQuery = "select count(d) from Dish d where d.vendor.id = :vendorId "
                    + "and (:includeHidden = true or d.active = true)")
    Page<Dish> findForVendor(@Param("vendorId") Long vendorId,
                             @Param("includeHidden") boolean includeHidden,
                             Pageable pageable);

    boolean existsByVendorIdAndNameIgnoreCase(Long vendorId, String name);

    boolean existsByVendorIdAndNameIgnoreCaseAndIdNot(Long vendorId, String name, Long id);
}
