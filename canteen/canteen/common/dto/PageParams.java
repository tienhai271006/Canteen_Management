package vn.edu.phenikaa.canteen.common.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Chuẩn hóa tham số phân trang từ client: không cho trang âm và chặn kích thước quá lớn. */
public final class PageParams {

    public static final int MAX_SIZE = 100;

    private PageParams() {
    }

    public static Pageable of(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
    }
}
