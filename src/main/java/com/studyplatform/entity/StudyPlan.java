package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * StudyPlan — AI-generated daily/weekly study schedule.
 * The plan_data field stores the full AI-generated JSON schedule.
 */
@Entity
@Table(name = "study_plans", indexes = {
    @Index(name = "idx_plan_user", columnList = "user_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamType examType;

    /** Hours available per day for study */
    private double availableHoursPerDay;

    /** Comma-separated strong subjects */
    @Column(columnDefinition = "TEXT")
    private String strengths;

    /** Comma-separated weak subjects */
    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    /** Full AI-generated plan stored as JSON */
    @Column(columnDefinition = "TEXT")
    private String planData;

    @Builder.Default
    private boolean isActive = true;

    @OneToMany(mappedBy = "studyPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StudyPlanEntry> entries = new ArrayList<>();

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
