package com.studyplatform.repository;

import com.studyplatform.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    List<TestResult> findByUserIdOrderByCompletedAtDesc(Long userId);
    Optional<TestResult> findByMockTestId(Long testId);

    @Query("SELECT AVG(r.percentage) FROM TestResult r WHERE r.user.id = :userId")
    Double findAveragePercentageByUserId(Long userId);

    @Query("SELECT COUNT(r) FROM TestResult r WHERE r.percentage < :percentage")
    long countBelowPercentage(double percentage);

    @Query("SELECT COUNT(r) FROM TestResult r")
    long countAllResults();
}
