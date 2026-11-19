package vn.edu.phenikaa.canteen.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class OcrDraftStoreTest {

    /** Đồng hồ chỉnh tay để kiểm tra hạn mà không phải chờ. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T01:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final MutableClock clock = new MutableClock();
    private final ParsedCard card = new ParsedCard("An", "21010123", null);

    @Test
    void draftIsBoundToItsOwner() {
        OcrDraftStore store = new OcrDraftStore(clock, Duration.ofMinutes(10), 10, 5);
        OcrDraftStore.Draft d = store.create(1, card).orElseThrow();
        assertThat(store.find(1, d.id())).isPresent();
        assertThat(store.find(2, d.id())).isEmpty();
        assertThat(store.discard(2, d.id())).isFalse();
        assertThat(store.find(1, d.id())).isPresent();
    }

    @Test
    void newScanReplacesTheOldDraft() {
        OcrDraftStore store = new OcrDraftStore(clock, Duration.ofMinutes(10), 10, 5);
        OcrDraftStore.Draft first = store.create(1, card).orElseThrow();
        OcrDraftStore.Draft second = store.create(1, card).orElseThrow();
        assertThat(store.find(1, first.id())).isEmpty();
        assertThat(store.find(1, second.id())).isPresent();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void draftDisappearsAtExpiryAndPurgeFreesMemory() {
        OcrDraftStore store = new OcrDraftStore(clock, Duration.ofMinutes(10), 10, 5);
        OcrDraftStore.Draft a = store.create(1, card).orElseThrow();
        store.create(2, card);
        clock.advance(Duration.ofMinutes(9));
        assertThat(store.find(1, a.id())).isPresent();
        clock.advance(Duration.ofMinutes(1));
        assertThat(store.find(1, a.id())).isEmpty();
        assertThat(store.purgeExpired()).isEqualTo(1);       // bản của user 2 còn lại
        assertThat(store.size()).isZero();
    }

    @Test
    void capRejectsNewUsersButStillLetsExistingUsersRescan() {
        OcrDraftStore store = new OcrDraftStore(clock, Duration.ofMinutes(10), 2, 5);
        store.create(1, card);
        store.create(2, card);
        assertThat(store.create(3, card)).isEmpty();
        assertThat(store.create(2, card)).isPresent();
    }

    @Test
    void scanRateLimitUsesSlidingWindowPerUser() {
        OcrDraftStore store = new OcrDraftStore(clock, Duration.ofMinutes(10), 10, 3);
        assertThat(store.tryAcquireScan(7)).isTrue();
        assertThat(store.tryAcquireScan(7)).isTrue();
        assertThat(store.tryAcquireScan(7)).isTrue();
        assertThat(store.tryAcquireScan(7)).isFalse();
        assertThat(store.tryAcquireScan(8)).isTrue();
        clock.advance(Duration.ofMinutes(61));
        assertThat(store.tryAcquireScan(7)).isTrue();
    }
}
