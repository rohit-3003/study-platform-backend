package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Subject entity — represents a subject within an exam type.
 * Example: "Indian Polity" for UPSC, "Quantitative Aptitude" for Banking.
 */
@Entity
@Table(name = "subjects", indexes = {
    @Index(name = "idx_subject_exam", columnList = "examType")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamType examType;

    @Column(nullable = false)
    private String name;

    private String description;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
