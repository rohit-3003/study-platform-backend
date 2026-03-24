package com.studyplatform.repository;

import com.studyplatform.entity.MockTest;
import com.studyplatform.entity.ExamType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface MockTestRepository extends JpaRepository<MockTest, Long> {
    List<MockTest> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<MockTest> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM MockTest m WHERE m.user.id = :userId AND m.createdAt >= :since")
    long countByUserIdSince(Long userId, LocalDateTime since);
    
    long countByUserId(Long userId);
}
