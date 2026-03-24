package com.studyplatform.controller;

import com.studyplatform.dto.ApiResponse;
import com.studyplatform.dto.StudyDto;
import com.studyplatform.service.MockTestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/mock-tests")
public class MockTestController {

    private final MockTestService mockTestService;

    public MockTestController(MockTestService mockTestService) {
        this.mockTestService = mockTestService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudyDto.MockTestResponse>> createTest(
            @Valid @RequestBody StudyDto.CreateMockTestRequest request) {
        var test = mockTestService.createMockTest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Mock test created", test));
    }

    @GetMapping("/{testId}")
    public ResponseEntity<ApiResponse<StudyDto.MockTestResponse>> getTest(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(mockTestService.getTest(testId)));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<StudyDto.TestResultResponse>> submitTest(
            @Valid @RequestBody StudyDto.SubmitTestRequest request) {
        var result = mockTestService.submitTest(request);
        return ResponseEntity.ok(ApiResponse.success("Test submitted successfully", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudyDto.MockTestResponse>>> getUserTests() {
        return ResponseEntity.ok(ApiResponse.success(mockTestService.getUserTests()));
    }
}
