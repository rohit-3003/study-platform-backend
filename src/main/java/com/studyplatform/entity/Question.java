package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Question entity — stores both manually created and AI-generated questions.
 * Options are stored as JSONB for flexible MCQ structures.
 */
@Entity
@Table(name = "questions", indexes = {
    @Index(name = "idx_question_exam", columnList = "examType"),
    @Index(name = "idx_question_subject", columnList = "subject_id"),
    @Index(name = "idx_question_difficulty", columnList = "difficulty")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamType examType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private QuestionType questionType = QuestionType.MCQ;

    /** MCQ options stored as JSON: ["Option A", "Option B", "Option C", "Option D"] */
    @Column(columnDefinition = "TEXT")
    private String options;

    @Column(nullable = false)
    private String correctAnswer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Builder.Default
    private boolean isAiGenerated = false;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum QuestionType {
        MCQ,
        DESCRIPTIVE,
        TRUE_FALSE
    }
}
