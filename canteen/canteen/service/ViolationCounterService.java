package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.IncidentProperties;
import vn.edu.phenikaa.canteen.model.FeedbackStatus;
import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.repository.FeedbackRepository;
import vn.edu.phenikaa.canteen.repository.IncidentRepository;

/**
 * Đếm vi phạm trong cửa sổ trượt (BR26, BR27). Chỉ sự việc CONFIRMED (không phải EXEMPTED, REVOKED, PENDING_REVIEW)
 * có thời điểm xảy ra trong 30 ngày gần nhất mới được cộng. Chỉ đọc, không đổi gì, nên xem số đếm không làm thay đổi nó (UC1.9).
 * Không tự gắn transaction: chạy trong transaction của người gọi để thấy cả thay đổi chưa commit của chính nó.
 */
@Service
@RequiredArgsConstructor
public class ViolationCounterService {

    public enum Level { NONE, REMINDER, WARNING, ESCALATED }

    private final IncidentRepository incidents;
    private final FeedbackRepository feedbacks;
    private final IncidentProperties props;
    private final Clock clock;

    public int countValid(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return (int) incidents.countInWindow(userId, IncidentStatus.CONFIRMED, now.minusDays(props.windowDays()), now);
    }

    /** Lần 1 nhắc, lần 2 cảnh báo, từ lần thứ {@code escalateAt} chuyển xem xét (không tự khóa tài khoản). */
    public Level levelFor(int count) {
        if (count <= 0) {
            return Level.NONE;
        }
        if (count >= props.escalateAt()) {
            return Level.ESCALATED;
        }
        return count == 1 ? Level.REMINDER : Level.WARNING;
    }

    /** BR35: có phản hồi đang mở trên sự việc đã xác nhận còn trong cửa sổ thì quyết định bất lợi mới phải chờ. */
    public boolean hasDisputed(Long userId) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(props.windowDays());
        return feedbacks.countOpenOnConfirmed(userId, FeedbackStatus.OPEN, IncidentStatus.CONFIRMED, since) > 0;
    }
}
