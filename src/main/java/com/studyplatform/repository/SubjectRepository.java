package com.studyplatform.repository;

import com.studyplatform.entity.Subject;
import com.studyplatform.entity.ExamType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByExamType(ExamType examType);
}
