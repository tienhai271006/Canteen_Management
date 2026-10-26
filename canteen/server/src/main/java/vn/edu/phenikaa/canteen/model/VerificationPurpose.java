package vn.edu.phenikaa.canteen.model;

public enum VerificationPurpose {
    /** Xác minh email hiện tại của tài khoản. */
    VERIFY,
    /** Xác minh email mới trước khi thay email đăng nhập (UC1.8). */
    CHANGE_EMAIL
}
