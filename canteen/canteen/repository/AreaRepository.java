package vn.edu.phenikaa.canteen.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.phenikaa.canteen.model.Area;

public interface AreaRepository extends JpaRepository<Area, Long> {

    List<Area> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
