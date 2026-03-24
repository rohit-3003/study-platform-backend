package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

/**
 * Individual entry in a study plan — represents one study block.
 */
@Entity
@Table(name = "study_plan_entries")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class StudyPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private StudyPlan studyPlan;

    /** 0=Sunday, 1=Monday, ..., 6=Saturday */
    private int dayOfWeek;

    @Column(nullable = false)
    private String subject;

    private String topic;

    private LocalTime startTime;
    private LocalTime endTime;

    /** 1=Low, 2=Medium, 3=High */
    @Builder.Default
    private int priority = 2;

    @Builder.Default
    private boolean isCompleted = false;
}
