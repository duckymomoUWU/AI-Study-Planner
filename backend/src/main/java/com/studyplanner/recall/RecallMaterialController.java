package com.studyplanner.recall;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.auth.AppUserPrincipal;

@RestController
public class RecallMaterialController {

    private final RecallService recallService;

    public RecallMaterialController(RecallService recallService) {
        this.recallService = recallService;
    }

    @PostMapping({"/api/v1/recall/materials", "/api/recall/materials"})
    public ResponseEntity<RecallDTOs.SingleDataResponse<RecallDTOs.MaterialResponse>> createMaterial(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody RecallDTOs.MaterialRequest request
    ) {
        RecallDTOs.MaterialResponse response = recallService.createMaterial(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RecallDTOs.SingleDataResponse<>(response));
    }

    @GetMapping({"/api/v1/recall/materials", "/api/recall/materials"})
    public ResponseEntity<RecallDTOs.PagedResponse<RecallDTOs.MaterialResponse>> listMaterials(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) UUID goalId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        RecallDTOs.PagedResponse<RecallDTOs.MaterialResponse> response = recallService.listMaterials(
                principal.id(), goalId, page, limit
        );
        return ResponseEntity.ok(response);
    }
}
