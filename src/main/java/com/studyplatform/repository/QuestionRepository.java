package com.studyplatform.repository;

import com.studyplatform.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByExamType(ExamType examType);
    List<Question> findByExamTypeAndDifficulty(ExamType examType, Difficulty difficulty);
    List<Question> findBySubjectId(Long subjectId);
    Page<Question> findByExamType(ExamType examType, Pageable pageable);
    long countByExamType(ExamType examType);
}
