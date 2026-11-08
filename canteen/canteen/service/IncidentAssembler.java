package vn.edu.phenikaa.canteen.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.dto.IncidentResponse;
import vn.edu.phenikaa.canteen.model.Incident;

/** Dựng IncidentResponse cho cả trang bằng vài câu truy vấn theo lô, tránh N+1. */
@Component
@RequiredArgsConstructor
public class IncidentAssembler {

    private record Row(Long id, String a, String b) {
    }

    private final JdbcClient jdbc;

    public List<IncidentResponse> toResponses(List<Incident> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Incident::getId).toList();
        List<Long> orderIds = list.stream().map(Incident::getOrderId).distinct().toList();
        List<Long> userIds = list.stream().map(Incident::getUserId).distinct().toList();

        Map<Long, Row> orders = new HashMap<>();
        jdbc.sql("SELECT o.id, o.order_code, v.name FROM orders o JOIN vendor v ON v.id = o.vendor_id "
                        + "WHERE o.id IN (:ids)")
                .param("ids", orderIds)
                .query((rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3)))
                .list().forEach(r -> orders.put(r.id(), r));

        Map<Long, String> names = new HashMap<>();
        jdbc.sql("SELECT id, full_name FROM user_account WHERE id IN (:ids)")
                .param("ids", userIds)
                .query((rs, n) -> new Row(rs.getLong(1), rs.getString(2), null))
                .list().forEach(r -> names.put(r.id(), r.a()));

        Set<Long> disputed = new HashSet<>(jdbc.sql(
                        "SELECT incident_id FROM feedback WHERE status = 'OPEN' AND incident_id IN (:ids)")
                .param("ids", ids).query(Long.class).list());

        return list.stream().map(i -> {
            Row o = orders.get(i.getOrderId());
            return new IncidentResponse(i.getId(), i.getOrderId(), o == null ? null : o.a(), i.getVendorId(),
                    o == null ? null : o.b(), i.getUserId(), names.get(i.getUserId()), i.getType(), i.getStatus(),
                    i.getOccurredAt(), i.getDecidedAt(), i.getDecisionNote(), disputed.contains(i.getId()));
        }).toList();
    }

    public IncidentResponse toResponse(Incident incident) {
        return toResponses(List.of(incident)).get(0);
    }
}
