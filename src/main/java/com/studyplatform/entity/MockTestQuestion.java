package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Junction table linking questions to a mock test, 
 * storing the user's answer and time spent.
 */
@Entity
@Table(name = "mock_test_questions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MockTestQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private MockTest mockTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** The answer submitted by the user */
    private String userAnswer;

    private Boolean isCorrect;

    /** Time spent on this question in seconds */
    @Builder.Default
    private int timeSpentSeconds = 0;

    /** Order of the question in the test */
    private int questionOrder;
}
