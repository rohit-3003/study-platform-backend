package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Topic entity — a subdivision of a Subject.
 * Example: "Fundamental Rights" under "Indian Polity".
 */
@Entity
@Table(name = "topics")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Difficulty difficulty = Difficulty.MEDIUM;

    /** Weight/importance of this topic in the exam (1-10) */
    @Builder.Default
    private int weight = 5;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
