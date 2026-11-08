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
import vn.edu.phenikaa.canteen.dto.ReasonRequest;
import vn.edu.phenikaa.canteen.dto.RestrictionRequest;
import vn.edu.phenikaa.canteen.dto.RestrictionResponse;
import vn.edu.phenikaa.canteen.dto.ViolationSummaryResponse;
import vn.edu.phenikaa.canteen.model.FeedbackStatus;
import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.model.RestrictionStatus;
import vn.edu.phenikaa.canteen.service.FeedbackService;
import vn.edu.phenikaa.canteen.service.IncidentService;
import vn.edu.phenikaa.canteen.service.RestrictionService;

/** UC10.3, UC10.5, UC10.6 cho quản trị viên. /api/admin/** đã giới hạn ADMIN ở SecurityConfig, service kiểm tra lại. */
@Tag(name = "Admin - Incidents and restrictions")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminIncidentController {

    private final IncidentService incidents;
    private final RestrictionService restrictions;
    private final FeedbackService feedback;

    @GetMapping("/incidents")
    public PageResponse<IncidentResponse> list(@RequestParam(required = false) Long userId,
                                               @RequestParam(required = false) Set<IncidentStatus> status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return incidents.listForAdmin(userId, status, page, size);
    }

    @PostMapping("/incidents/{incidentId}/decision")
    public IncidentResponse decide(@PathVariable Long incidentId, @Valid @RequestBody IncidentDecisionRequest req) {
        return incidents.decide(incidentId, req.decision(), req.note());
    }

    @PostMapping("/incidents/{incidentId}/revoke")
    public IncidentResponse revoke(@PathVariable Long incidentId, @Valid @RequestBody ReasonRequest req) {
        return incidents.revoke(incidentId, req.reason());
    }

    @GetMapping("/users/{userId}/violations")
    public ViolationSummaryResponse violations(@PathVariable Long userId) {
        return restrictions.summary(userId);
    }

    @GetMapping("/restrictions")
    public PageResponse<RestrictionResponse> restrictions(@RequestParam(required = false) Long userId,
                                                          @RequestParam(required = false) Set<RestrictionStatus> status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return restrictions.list(userId, status, page, size);
    }

    @PostMapping("/restrictions")
    public RestrictionResponse restrict(@Valid @RequestBody RestrictionRequest req) {
        return restrictions.decide(req);
    }

    @PostMapping("/restrictions/{restrictionId}/revoke")
    public RestrictionResponse revokeRestriction(@PathVariable Long restrictionId, @Valid @RequestBody ReasonRequest req) {
        return restrictions.revoke(restrictionId, req.reason());
    }

    @GetMapping("/feedback")
    public PageResponse<FeedbackResponse> feedback(@RequestParam(required = false) Set<FeedbackStatus> status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return feedback.listForAdmin(status, page, size);
    }

    @PostMapping("/feedback/{feedbackId}/resolve")
    public FeedbackResponse resolve(@PathVariable Long feedbackId, @Valid @RequestBody FeedbackResolveRequest req) {
        return feedback.resolve(feedbackId, req.response(), req.action());
    }
}
