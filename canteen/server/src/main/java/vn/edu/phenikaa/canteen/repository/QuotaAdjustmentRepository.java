package vn.edu.phenikaa.canteen.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.phenikaa.canteen.model.QuotaAdjustment;

public interface QuotaAdjustmentRepository extends JpaRepository<QuotaAdjustment, Long> {

    List<QuotaAdjustment> findByQuotaIdOrderByIdDesc(Long quotaId);

    boolean existsByQuotaId(Long quotaId);
}
