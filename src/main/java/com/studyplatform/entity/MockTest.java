package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MockTest entity — represents a test session taken by a user.
 */
@Entity
@Table(name = "mock_tests", indexes = {
    @Index(name = "idx_mocktest_user", columnList = "user_id"),
    @Index(name = "idx_mocktest_exam", columnList = "examType")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MockTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamType examType;

    private String title;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Difficulty difficulty = Difficulty.MEDIUM;

    private int totalQuestions;

    /** Duration in minutes */
    @Builder.Default
    private int durationMinutes = 60;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TestStatus status = TestStatus.CREATED;

    @OneToMany(mappedBy = "mockTest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MockTestQuestion> questions = new ArrayList<>();

    @OneToOne(mappedBy = "mockTest", cascade = CascadeType.ALL)
    private TestResult result;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TestStatus {
        CREATED, IN_PROGRESS, COMPLETED, EXPIRED
    }
}
