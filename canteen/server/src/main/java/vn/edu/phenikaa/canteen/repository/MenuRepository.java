package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.phenikaa.canteen.model.MealSession;
import vn.edu.phenikaa.canteen.model.Menu;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    boolean existsByVendorIdAndMenuDateAndMealSession(Long vendorId, LocalDate menuDate, MealSession mealSession);

    List<Menu> findByVendorIdAndMenuDateBetweenOrderByMenuDateAscMealSessionAsc(Long vendorId, LocalDate from,
                                                                                LocalDate to);
}
