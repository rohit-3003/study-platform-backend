package com.studyplatform.controller;

import com.studyplatform.dto.ApiResponse;
import com.studyplatform.dto.StudyDto;
import com.studyplatform.service.StudyPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/study-plans")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudyDto.StudyPlanResponse>> createPlan(
            @Valid @RequestBody StudyDto.CreateStudyPlanRequest request) {
        var plan = studyPlanService.createStudyPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Study plan generated successfully", plan));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<StudyDto.StudyPlanResponse>> getActivePlan() {
        return ResponseEntity.ok(ApiResponse.success(studyPlanService.getActivePlan()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudyDto.StudyPlanResponse>>> getAllPlans() {
        return ResponseEntity.ok(ApiResponse.success(studyPlanService.getAllPlans()));
    }

    @PatchMapping("/entries/{entryId}/complete")
    public ResponseEntity<ApiResponse<Void>> markCompleted(@PathVariable Long entryId) {
        studyPlanService.markEntryCompleted(entryId);
        return ResponseEntity.ok(ApiResponse.success("Entry marked as completed", null));
    }
}
