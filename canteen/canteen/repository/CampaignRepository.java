package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Campaign;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /** Chiến dịch đang hiệu lực tại một thời điểm; nếu có nhiều thì lấy cái mới nhất. */
    Optional<Campaign> findFirstByActiveTrueAndStartsAtLessThanEqualAndEndsAtAfterOrderByIdDesc(
            LocalDateTime startsAtBefore, LocalDateTime endsAtAfter);

    /** Có chiến dịch active khác chồng thời gian không. {@code excludeId} là -1 khi tạo mới. */
    @Query("select count(c) > 0 from Campaign c "
            + "where c.active = true and c.id <> :excludeId and c.startsAt < :endsAt and c.endsAt > :startsAt")
    boolean existsActiveOverlap(@Param("excludeId") Long excludeId,
                                @Param("startsAt") LocalDateTime startsAt,
                                @Param("endsAt") LocalDateTime endsAt);
}
