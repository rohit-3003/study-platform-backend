package com.studyplatform.dto;

import com.studyplatform.entity.Difficulty;
import com.studyplatform.entity.ExamType;
import com.studyplatform.entity.Question;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

public class StudyDto {

    // ========== Study Plan DTOs ==========

    @Data
    public static class CreateStudyPlanRequest {
        @NotNull(message = "Exam type is required")
        private ExamType examType;

        @Min(1) @Max(16)
        private double availableHoursPerDay;

        private List<String> strengths;
        private List<String> weaknesses;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class StudyPlanResponse {
        private Long id;
        private String examType;
        private double availableHoursPerDay;
        private List<String> strengths;
        private List<String> weaknesses;
        private Object planData;  // parsed JSON
        private List<StudyPlanEntryDto> entries;
        private boolean isActive;
        private String createdAt;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class StudyPlanEntryDto {
        private Long id;
        private int dayOfWeek;
        private String subject;
        private String topic;
        private String startTime;
        private String endTime;
        private int priority;
        private boolean isCompleted;
    }

    // ========== Mock Test DTOs ==========

    @Data
    public static class CreateMockTestRequest {
        @NotNull
        private ExamType examType;

        private Long subjectId;  // optional: specific subject

        @Builder.Default
        private Difficulty difficulty = Difficulty.MEDIUM;

        @Min(5) @Max(100)
        private int totalQuestions;

        @Min(10) @Max(180)
        private int durationMinutes;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class MockTestResponse {
        private Long id;
        private String title;
        private String examType;
        private String difficulty;
        private int totalQuestions;
        private int durationMinutes;
        private String status;
        private List<QuestionDto> questions;
        private String createdAt;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class QuestionDto {
        private Long id;
        private String questionText;
        private String questionType;
        private List<String> options;
        private String difficulty;
        private String subject;
        private String topic;
        // NOTE: correctAnswer and explanation are NOT included in test-taking mode
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class QuestionWithAnswerDto {
        private Long id;
        private String questionText;
        private String questionType;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private String difficulty;
        private String userAnswer;
        private boolean isCorrect;
    }

    @Data
    public static class SubmitTestRequest {
        @NotNull
        private Long testId;
        
        private List<AnswerSubmission> answers;
        private int timeTakenSeconds;
    }

    @Data
    public static class AnswerSubmission {
        private Long questionId;
        private String answer;
        private int timeSpentSeconds;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class TestResultResponse {
        private Long testId;
        private int score;
        private int totalMarks;
        private double percentage;
        private int timeTaken;
        private double rankPercentile;
        private List<QuestionWithAnswerDto> detailedResults;
        private Object subjectWiseScore;
        private String completedAt;
    }

    // ========== Rank Predictor DTOs ==========

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class RankPrediction {
        private double averageScore;
        private double percentile;
        private int estimatedRank;
        private int totalCandidates;
        private String examType;
        private List<SubjectPerformance> subjectPerformance;
        private String recommendation;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class SubjectPerformance {
        private String subject;
        private double averageScore;
        private String strength;  // STRONG, AVERAGE, WEAK
    }

    // ========== Dashboard DTOs ==========

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class DashboardStats {
        private int totalTestsTaken;
        private double averageScore;
        private int currentStreak;
        private int longestStreak;
        private double rankPercentile;
        private List<ScoreHistory> scoreHistory;
        private List<SubjectPerformance> weakAreas;
        private List<RecentActivity> recentActivities;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class ScoreHistory {
        private String date;
        private double score;
        private String testTitle;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class RecentActivity {
        private String type;
        private String description;
        private String date;
        private int score;
    }
}
