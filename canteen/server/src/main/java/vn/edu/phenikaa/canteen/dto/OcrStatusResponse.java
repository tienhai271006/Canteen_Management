package vn.edu.phenikaa.canteen.dto;

/** Cho giao diện biết có hiện nút quét thẻ hay chỉ nhập tay (OCR là tùy chọn, BR09). */
public record OcrStatusResponse(boolean available) {
}
