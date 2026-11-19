package vn.edu.phenikaa.canteen.controller;

import java.util.Set;

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
import vn.edu.phenikaa.canteen.dto.FeedbackResolveRequest;
import vn.edu.phenikaa.canteen.dto.FeedbackResponse;
import vn.edu.phenikaa.canteen.dto.IncidentDecisionRequest;
import vn.edu.phenikaa.canteen.dto.IncidentResponse;
import vn.edu.phenikaa.canteen.model.FeedbackStatus;
import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.service.FeedbackService;
import vn.edu.phenikaa.canteen.service.IncidentService;

/** UC10.3: quản lý chỉ xét sự việc của quầy mình (service kiểm tra quầy từ chính sự việc). */
@Tag(name = "Manager - Incidents")
@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerIncidentController {

    private final IncidentService incidents;
    private final FeedbackService feedback;

    @GetMapping("/incidents")
    public PageResponse<IncidentResponse> list(@RequestParam Long vendorId,
                                               @RequestParam(required = false) Set<IncidentStatus> status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return incidents.listForVendor(vendorId, status, page, size);
    }

    @PostMapping("/incidents/{incidentId}/decision")
    public IncidentResponse decide(@PathVariable Long incidentId, @Valid @RequestBody IncidentDecisionRequest req) {
        return incidents.decide(incidentId, req.decision(), req.note());
    }

    @GetMapping("/feedback")
    public PageResponse<FeedbackResponse> feedback(@RequestParam Long vendorId,
                                                   @RequestParam(required = false) Set<FeedbackStatus> status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return feedback.listForVendor(vendorId, status, page, size);
    }

    @PostMapping("/feedback/{feedbackId}/resolve")
    public FeedbackResponse resolve(@PathVariable Long feedbackId, @Valid @RequestBody FeedbackResolveRequest req) {
        return feedback.resolve(feedbackId, req.response(), req.action());
    }
}
