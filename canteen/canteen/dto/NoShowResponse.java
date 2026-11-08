package vn.edu.phenikaa.canteen.dto;

import vn.edu.phenikaa.canteen.model.IncidentStatus;

public record NoShowResponse(OrderResponse order, Long incidentId, IncidentStatus incidentStatus) {
}
