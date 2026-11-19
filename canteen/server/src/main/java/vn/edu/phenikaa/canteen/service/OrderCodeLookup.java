package vn.edu.phenikaa.canteen.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.repository.OrderRepository;

/** Lấy mã đơn hiển thị cho danh sách yêu cầu, một câu truy vấn cho cả trang (tránh N+1). */
@Component
@RequiredArgsConstructor
public class OrderCodeLookup {

    private final OrderRepository orders;

    public Map<Long, String> codes(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orders.findAllById(new HashSet<>(orderIds)).stream()
                .collect(Collectors.toMap(Order::getId, Order::getOrderCode));
    }
}
