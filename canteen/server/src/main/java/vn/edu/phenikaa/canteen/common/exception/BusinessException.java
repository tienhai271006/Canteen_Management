package vn.edu.phenikaa.canteen.common.exception;

import lombok.Getter;

/**
 * Lỗi nghiệp vụ có chủ đích. Ném ra từ service, GlobalExceptionHandler đổi thành JSON thống nhất.
 * Là RuntimeException nên làm {@code @Transactional} rollback (đúng ý với BR11: lỗi thì không để lại giữ chỗ).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public static BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN);
    }
}
