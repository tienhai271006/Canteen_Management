package vn.edu.phenikaa.canteen.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.FeedbackRequest;
import vn.edu.phenikaa.canteen.dto.FeedbackResponse;
import vn.edu.phenikaa.canteen.dto.IncidentResponse;
import vn.edu.phenikaa.canteen.service.FeedbackService;
import vn.edu.phenikaa.canteen.service.IncidentService;

/** Sự việc và phản hồi của chính khách. Hạn chế đặt trước không chặn các API này (BR31). */
@Tag(name = "My incidents")
@RestController
@RequestMapping("/api/me/incidents")
@RequiredArgsConstructor
public class FeedbackController {

    private final IncidentService incidents;
    private final FeedbackService feedback;

    @GetMapping
    public PageResponse<IncidentResponse> mine(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return incidents.listMine(CurrentUser.id(), page, size);
    }

    @PostMapping("/{incidentId}/feedback")
    public ResponseEntity<FeedbackResponse> submit(@PathVariable Long incidentId,
                                                   @Valid @RequestBody FeedbackRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedback.submit(incidentId, req.content()));
    }
}
