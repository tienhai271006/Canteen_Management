package vn.edu.phenikaa.canteen.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Lịch sử điều chỉnh hạn mức (BR33, NFR03). Chỉ ghi thêm, không sửa, nên không kế thừa BaseEntity. */
@Getter
@Setter
@Entity
@Table(name = "quota_adjustment")
public class QuotaAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quota_id", nullable = false)
    private Long quotaId;

    @Column(name = "delta_total", nullable = false)
    private int deltaTotal;

    @Column(name = "delta_counter", nullable = false)
    private int deltaCounter;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
