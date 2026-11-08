package vn.edu.phenikaa.canteen.ocr;

/** Các trường đọc được từ thẻ. Trường nào không đọc được thì null; mọi trường chỉ là GỢI Ý để người dùng kiểm tra. */
public record ParsedCard(String fullName, String studentCode, String email) {

    public boolean isEmpty() {
        return fullName == null && studentCode == null && email == null;
    }

    /** Có ít nhất họ tên hoặc mã sinh viên thì mới đáng để người dùng xác nhận. */
    public boolean usable() {
        return fullName != null || studentCode != null;
    }
}
