package vn.edu.phenikaa.canteen.ocr;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Kho bản nháp OCR tạm thời (BR10, NFR07). Chỉ giữ CHỮ đã trích xuất, ảnh gốc không bao giờ được giữ lại ở đâu cả.
 * <ul>
 *   <li>Bản nháp gắn với đúng một người dùng: người khác biết mã cũng không đọc hay xác nhận được (BR03).</li>
 *   <li>Mỗi người chỉ có một bản nháp; quét lại thì bản cũ bị xóa.</li>
 *   <li>Hết hạn là biến mất ngay khi truy cập, và {@link #purgeExpired()} dọn định kỳ.</li>
 *   <li>Có trần số bản nháp và trần số lượt quét mỗi giờ mỗi người để không bị lạm dụng.</li>
 * </ul>
 * Lưu trong bộ nhớ của MỘT tiến trình: chạy nhiều bản server cần sticky session, hoặc thay lớp này bằng kho dùng chung
 * (ví dụ Redis có TTL). Mất bản nháp khi khởi động lại là chấp nhận được, người dùng chỉ cần quét lại.
 */
public final class OcrDraftStore {

    public record Draft(String id, long userId, ParsedCard card, Instant createdAt, Instant expiresAt) {
    }

    private static final Duration RATE_WINDOW = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;
    private final Duration ttl;
    private final int maxDrafts;
    private final int maxScansPerHour;

    private final Map<String, Draft> byId = new HashMap<>();
    private final Map<Long, String> idByUser = new HashMap<>();
    private final Map<Long, Deque<Instant>> scans = new HashMap<>();

    public OcrDraftStore(Clock clock, Duration ttl, int maxDrafts, int maxScansPerHour) {
        this.clock = clock;
        this.ttl = ttl;
        this.maxDrafts = maxDrafts;
        this.maxScansPerHour = maxScansPerHour;
    }

    /** Ghi nhận một lượt quét của người dùng. @return false nếu đã quá số lượt cho phép trong giờ qua. */
    public synchronized boolean tryAcquireScan(long userId) {
        Instant now = clock.instant();
        Deque<Instant> q = scans.computeIfAbsent(userId, k -> new ArrayDeque<>());
        while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(RATE_WINDOW))) {
            q.pollFirst();
        }
        if (q.size() >= maxScansPerHour) {
            return false;
        }
        q.addLast(now);
        return true;
    }

    /** @return bản nháp mới, hoặc empty nếu kho đã đầy (sau khi dọn bản hết hạn). */
    public synchronized Optional<Draft> create(long userId, ParsedCard card) {
        purgeExpired();
        String old = idByUser.get(userId);
        if (old == null && byId.size() >= maxDrafts) {
            return Optional.empty();
        }
        if (old != null) {
            byId.remove(old);
        }
        Instant now = clock.instant();
        Draft d = new Draft(newId(), userId, card, now, now.plus(ttl));
        byId.put(d.id(), d);
        idByUser.put(userId, d.id());
        return Optional.of(d);
    }

    /** Chỉ trả về nếu còn hạn VÀ đúng chủ; nếu đã hết hạn thì xóa luôn. */
    public synchronized Optional<Draft> find(long userId, String id) {
        if (id == null) {
            return Optional.empty();
        }
        Draft d = byId.get(id);
        if (d == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(d.expiresAt())) {
            remove(d);
            return Optional.empty();
        }
        return d.userId() == userId ? Optional.of(d) : Optional.empty();
    }

    /** @return true nếu có bản nháp của người này với mã này và đã xóa. */
    public synchronized boolean discard(long userId, String id) {
        Draft d = id == null ? null : byId.get(id);
        if (d == null || d.userId() != userId) {
            return false;
        }
        remove(d);
        return true;
    }

    /** @return số bản nháp đã xóa. Cũng dọn bộ đếm lượt quét đã ra ngoài cửa sổ. */
    public synchronized int purgeExpired() {
        Instant now = clock.instant();
        int removed = 0;
        for (Iterator<Draft> it = byId.values().iterator(); it.hasNext(); ) {
            Draft d = it.next();
            if (!now.isBefore(d.expiresAt())) {
                idByUser.remove(d.userId(), d.id());
                it.remove();
                removed++;
            }
        }
        scans.values().removeIf(q -> {
            while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(RATE_WINDOW))) {
                q.pollFirst();
            }
            return q.isEmpty();
        });
        return removed;
    }

    public synchronized int size() {
        return byId.size();
    }

    private void remove(Draft d) {
        byId.remove(d.id());
        idByUser.remove(d.userId(), d.id());
    }

    private static String newId() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
