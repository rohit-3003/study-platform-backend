package com.studyplatform.controller;

import com.studyplatform.dto.ApiResponse;
import com.studyplatform.dto.StudyDto;
import com.studyplatform.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<StudyDto.DashboardStats>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboardStats()));
    }

    @GetMapping("/rank-prediction")
    public ResponseEntity<ApiResponse<StudyDto.RankPrediction>> getRankPrediction() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getRankPrediction()));
    }
}
