package vn.edu.phenikaa.canteen.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Danh mục mã lỗi dùng chung. Client dựa vào {@code name()} để xử lý, không dựa vào câu chữ.
 * Chú thích BR chỉ quy tắc nghiệp vụ trong tài liệu đặc tả. Thêm mã mới ở pha tương ứng.
 */
@Getter
public enum ErrorCode {

    // ---- Chung ----
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không đọc được hoặc sai định dạng."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),   // BR03
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu."),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu đã thay đổi hoặc bị trùng, vui lòng tải lại và thử lại."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "Dữ liệu vừa được người khác cập nhật, vui lòng tải lại."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống."),

    // ---- UC1 Tài khoản (BR06, BR08) ----
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."),
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "Email đã được sử dụng."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email trường chưa được xác minh."),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "Mã xác minh không đúng."),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "Mã xác minh đã hết hạn."),
    OTP_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã nhập sai quá số lần cho phép, vui lòng yêu cầu mã mới."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đang bị vô hiệu hóa."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại."),
    CURRENT_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng."),
    STUDENT_CODE_ALREADY_USED(HttpStatus.CONFLICT, "Mã sinh viên đã gắn với tài khoản khác."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "Email đã được xác minh."),
    SCHOOL_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "Chỉ email thuộc miền của trường mới cần xác minh."),
    OTP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn yêu cầu mã quá nhanh hoặc quá nhiều lần, vui lòng thử lại sau."),

    // ---- UC2 Điểm bán (BR02) ----
    VENDOR_NOT_ACTIVE(HttpStatus.CONFLICT, "Quầy hiện không nhận đơn mới."),
    VENDOR_INVALID_STATE(HttpStatus.CONFLICT, "Quầy không ở trạng thái phù hợp để thực hiện thao tác này."),
    VENDOR_NAME_DUPLICATE(HttpStatus.CONFLICT, "Khu vực này đã có quầy trùng tên."),
    MEMBER_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "Tài khoản đã được phân công cho quầy này."),
    MEMBER_ROLE_MISMATCH(HttpStatus.CONFLICT, "Vai trò của tài khoản không phù hợp với vị trí được phân công."),
    LAST_MANAGER_REQUIRED(HttpStatus.CONFLICT, "Quầy phải còn ít nhất một quản lý."),

    // ---- UC3/UC4 Thực đơn (BR02, BR33) ----
    DISH_UNAVAILABLE(HttpStatus.CONFLICT, "Món hiện không bán."),
    PICKUP_SLOT_INVALID(HttpStatus.CONFLICT, "Khung giờ nhận không hợp lệ."),
    ORDER_CUTOFF_PASSED(HttpStatus.CONFLICT, "Đã quá giờ đóng nhận đơn của ca này."),
    QUOTA_BELOW_COMMITTED(HttpStatus.CONFLICT, "Không thể giảm hạn mức xuống dưới số suất đã cam kết."),
    DISH_NAME_DUPLICATE(HttpStatus.CONFLICT, "Quầy đã có món trùng tên."),
    DISH_VENDOR_MISMATCH(HttpStatus.BAD_REQUEST, "Món không thuộc quầy của thực đơn này."),
    MENU_ALREADY_EXISTS(HttpStatus.CONFLICT, "Quầy đã có thực đơn cho ngày và buổi này."),
    MENU_NOT_EDITABLE(HttpStatus.CONFLICT, "Thực đơn của ngày đã qua không thể chỉnh sửa."),
    MENU_INCOMPLETE(HttpStatus.CONFLICT, "Thực đơn cần ít nhất một món và một khung giờ nhận."),
    MENU_HAS_ORDERS(HttpStatus.CONFLICT, "Thực đơn đã có suất được đặt nên không thể chuyển về nháp."),
    MENU_ITEM_EXISTS(HttpStatus.CONFLICT, "Món đã có trong thực đơn này."),
    MENU_ITEM_IN_USE(HttpStatus.CONFLICT, "Món đã phát sinh đơn hoặc điều chỉnh hạn mức, chỉ có thể tạm ngừng bán."),
    SLOT_OVERLAP(HttpStatus.CONFLICT, "Khung giờ nhận bị trùng với khung đã có."),
    SLOT_IN_USE(HttpStatus.CONFLICT, "Khung giờ nhận đã có đơn nên không thể sửa hoặc xóa."),
    FILE_INVALID(HttpStatus.BAD_REQUEST, "Tệp không hợp lệ, chỉ nhận ảnh JPEG, PNG hoặc WebP."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá dung lượng cho phép."),

    // ---- UC5 Giỏ hàng và đặt món (BR01, BR11, BR12, BR36) ----
    CART_MULTI_VENDOR(HttpStatus.CONFLICT, "Giỏ hàng chỉ chứa món của một quầy."),
    CART_MULTI_MENU(HttpStatus.CONFLICT, "Giỏ hàng chỉ chứa món của cùng một thực đơn (cùng ngày và buổi). Hãy xóa giỏ hoặc đặt riêng."),
    CART_EMPTY(HttpStatus.CONFLICT, "Giỏ hàng đang trống."),
    QUOTA_EXCEEDED(HttpStatus.CONFLICT, "Không đủ suất cho món này."),
    DUPLICATE_ORDER_REQUEST(HttpStatus.CONFLICT, "Yêu cầu đặt đơn này đã được xử lý."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."),

    // ---- UC6/UC7 Trạng thái đơn và hủy (BR13, BR16, BR17, BR21) ----
    INVALID_ORDER_TRANSITION(HttpStatus.CONFLICT, "Đơn không thể chuyển sang trạng thái này."),
    ORDER_EXPIRED(HttpStatus.CONFLICT, "Đơn đã hết hạn xác nhận."),
    CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "Đơn ở trạng thái hiện tại không được hủy theo cách này."),
    CANCEL_REQUEST_PENDING(HttpStatus.CONFLICT, "Đơn đang có yêu cầu hủy chờ xử lý."),

    // ---- UC8 Ưu đãi (BR05, BR06, BR07) ----
    PROMOTION_NOT_ELIGIBLE(HttpStatus.CONFLICT, "Tài khoản không đủ điều kiện nhận ưu đãi."),

    // ---- UC10 Vi phạm và hạn chế (BR24, BR31) ----
    ACCOUNT_RESTRICTED(HttpStatus.FORBIDDEN, "Tài khoản đang bị hạn chế tạo đơn đặt trước."),
    INCIDENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Đơn này đã có ghi nhận vi phạm.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
