package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * TestResult — stores the final score and analytics for a completed mock test.
 */
@Entity
@Table(name = "test_results", indexes = {
    @Index(name = "idx_result_user", columnList = "user_id"),
    @Index(name = "idx_result_percentile", columnList = "rankPercentile")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private MockTest mockTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private int score;
    private int totalMarks;
    private double percentage;

    /** Time taken in seconds */
    private int timeTaken;

    /** Percentile rank among all users */
    private double rankPercentile;

    /** Subject-wise breakdown as JSON */
    @Column(columnDefinition = "TEXT")
    private String subjectWiseScore;

    @Builder.Default
    private LocalDateTime completedAt = LocalDateTime.now();
}
