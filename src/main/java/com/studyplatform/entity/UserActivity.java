package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UserActivity — tracks daily study activity for streaks and analytics.
 */
@Entity
@Table(name = "user_activities", indexes = {
    @Index(name = "idx_activity_user_date", columnList = "user_id, date")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    private int durationMinutes;
    private int score;

    @Column(nullable = false)
    private LocalDate date;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ActivityType {
        STUDY, MOCK_TEST, REVISION, PRACTICE
    }
}
