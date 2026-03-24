package com.studyplatform.controller;

import com.studyplatform.dto.ApiResponse;
import com.studyplatform.entity.Subject;
import com.studyplatform.entity.ExamType;
import com.studyplatform.repository.SubjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/subjects")
public class SubjectController {

    private final SubjectRepository subjectRepo;

    public SubjectController(SubjectRepository subjectRepo) {
        this.subjectRepo = subjectRepo;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Subject>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(subjectRepo.findAll()));
    }

    @GetMapping("/by-exam/{examType}")
    public ResponseEntity<ApiResponse<List<Subject>>> getByExam(@PathVariable ExamType examType) {
        return ResponseEntity.ok(ApiResponse.success(subjectRepo.findByExamType(examType)));
    }
}
