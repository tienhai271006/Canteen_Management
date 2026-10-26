package vn.edu.phenikaa.canteen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import vn.edu.phenikaa.canteen.common.config.TimeConfig;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.exception.GlobalExceptionHandler;

class ErrorHandlingTest {

    private final Clock fixed = Clock.fixed(Instant.parse("2026-10-25T03:00:00Z"), TimeConfig.ZONE);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(fixed);

    @Test
    void businessExceptionMapsToUnifiedJson() {
        var req = new MockHttpServletRequest("POST", "/api/orders");
        var res = handler.handleBusiness(new BusinessException(ErrorCode.QUOTA_EXCEEDED), req);

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().code()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(res.getBody().path()).isEqualTo("/api/orders");
        assertThat(res.getBody().timestamp().getOffset().getTotalSeconds()).isEqualTo(7 * 3600);
    }

    @Test
    void unexpectedErrorHidesInternalDetails() {
        var req = new MockHttpServletRequest("GET", "/api/x");
        var res = handler.handleUnexpected(new IllegalStateException("secret detail"), req);

        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().message()).doesNotContain("secret");
    }
}
