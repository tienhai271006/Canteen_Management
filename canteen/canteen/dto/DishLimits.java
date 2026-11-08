package vn.edu.phenikaa.canteen.dto;

final class DishLimits {

    /** 10 triệu đồng: chặn nhầm lẫn nhập thừa số 0, không phải quy tắc nghiệp vụ. */
    static final long MAX_PRICE = 10_000_000L;

    private DishLimits() {
    }
}
