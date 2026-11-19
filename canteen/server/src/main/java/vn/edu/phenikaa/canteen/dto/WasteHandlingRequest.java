package vn.edu.phenikaa.canteen.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record WasteHandlingRequest(@NotEmpty @Valid List<WasteItemRequest> items, @Size(max = 255) String note) {
}
