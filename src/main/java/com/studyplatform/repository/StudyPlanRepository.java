package com.studyplatform.repository;

import com.studyplatform.entity.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {
    List<StudyPlan> findByUserId(Long userId);
    Optional<StudyPlan> findByUserIdAndIsActiveTrue(Long userId);
    long countByUserId(Long userId);
}
