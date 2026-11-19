package vn.edu.phenikaa.canteen.model;

/** Khớp ck_email_outbox_status. Gửi lỗi vẫn PENDING cho đến khi hết số lần thử thì FAILED. */
public enum EmailOutboxStatus {
    PENDING, SENT, FAILED
}
