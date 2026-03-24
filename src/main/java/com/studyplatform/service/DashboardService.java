package com.studyplatform.service;

import com.studyplatform.dto.StudyDto;
import com.studyplatform.entity.*;
import com.studyplatform.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private final TestResultRepository resultRepo;
    private final MockTestRepository mockTestRepo;
    private final UserActivityRepository activityRepo;
    private final AuthService authService;

    public DashboardService(TestResultRepository resultRepo, MockTestRepository mockTestRepo,
                            UserActivityRepository activityRepo, AuthService authService) {
        this.resultRepo = resultRepo;
        this.mockTestRepo = mockTestRepo;
        this.activityRepo = activityRepo;
        this.authService = authService;
    }

    public StudyDto.DashboardStats getDashboardStats() {
        User user = authService.getCurrentUser();
        Long userId = user.getId();
        int totalTests = (int) mockTestRepo.countByUserId(userId);
        Double avgScore = resultRepo.findAveragePercentageByUserId(userId);
        double averageScore = avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0;
        List<LocalDate> activeDates = activityRepo.findDistinctDatesByUserId(userId);
        int currentStreak = calcCurrentStreak(activeDates);
        int longestStreak = calcLongestStreak(activeDates);
        List<TestResult> results = resultRepo.findByUserIdOrderByCompletedAtDesc(userId);
        double rankPercentile = results.stream().limit(5)
                .mapToDouble(TestResult::getRankPercentile).average().orElse(0);
        List<StudyDto.ScoreHistory> scoreHistory = results.stream().limit(10)
                .map(r -> StudyDto.ScoreHistory.builder()
                        .date(r.getCompletedAt().toLocalDate().toString())
                        .score(r.getPercentage())
                        .testTitle(r.getMockTest().getTitle()).build())
                .collect(Collectors.toList());
        List<StudyDto.RecentActivity> recent = results.stream().limit(5)
                .map(r -> StudyDto.RecentActivity.builder()
                        .type("MOCK_TEST").description(r.getMockTest().getTitle())
                        .date(r.getCompletedAt().toString()).score((int) r.getPercentage()).build())
                .collect(Collectors.toList());
        return StudyDto.DashboardStats.builder()
                .totalTestsTaken(totalTests).averageScore(averageScore)
                .currentStreak(currentStreak).longestStreak(longestStreak)
                .rankPercentile(Math.round(rankPercentile * 10.0) / 10.0)
                .scoreHistory(scoreHistory).weakAreas(List.of()).recentActivities(recent).build();
    }

    public StudyDto.RankPrediction getRankPrediction() {
        User user = authService.getCurrentUser();
        List<TestResult> results = resultRepo.findByUserIdOrderByCompletedAtDesc(user.getId());
        if (results.isEmpty()) {
            return StudyDto.RankPrediction.builder().averageScore(0).percentile(0)
                    .estimatedRank(0).totalCandidates(0)
                    .examType(user.getExamType() != null ? user.getExamType().name() : "UPSC")
                    .recommendation("Take at least one mock test to get your rank prediction!").build();
        }
        double avgScore = results.stream().mapToDouble(TestResult::getPercentage).average().orElse(0);
        long totalResults = resultRepo.countAllResults();
        long belowCount = resultRepo.countBelowPercentage(avgScore);
        double percentile = totalResults > 0 ? (belowCount * 100.0) / totalResults : 50;
        int totalCandidates = getCandidates(user.getExamType());
        int estimatedRank = Math.max(1, (int) ((100 - percentile) / 100.0 * totalCandidates));
        String rec = avgScore >= 80 ? "Excellent! Focus on maintaining consistency." :
                     avgScore >= 60 ? "Good progress! Target 2-3 mock tests per week." :
                     avgScore >= 40 ? "Building foundation. Focus on core concepts." :
                     "Keep practicing! Start with topic-wise tests.";
        return StudyDto.RankPrediction.builder()
                .averageScore(Math.round(avgScore * 10.0) / 10.0)
                .percentile(Math.round(percentile * 10.0) / 10.0)
                .estimatedRank(estimatedRank).totalCandidates(totalCandidates)
                .examType(user.getExamType() != null ? user.getExamType().name() : "UPSC")
                .subjectPerformance(List.of()).recommendation(rec).build();
    }

    private int calcCurrentStreak(List<LocalDate> dates) {
        if (dates.isEmpty()) return 0;
        Set<LocalDate> dateSet = new HashSet<>(dates);
        int streak = 0; LocalDate d = LocalDate.now();
        while (dateSet.contains(d)) { streak++; d = d.minusDays(1); }
        return streak;
    }

    private int calcLongestStreak(List<LocalDate> dates) {
        if (dates.isEmpty()) return 0;
        List<LocalDate> sorted = dates.stream().sorted().collect(Collectors.toList());
        int longest = 1, current = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (ChronoUnit.DAYS.between(sorted.get(i - 1), sorted.get(i)) == 1) {
                current++; longest = Math.max(longest, current);
            } else { current = 1; }
        }
        return longest;
    }

    private int getCandidates(ExamType type) {
        if (type == null) return 100000;
        return switch (type) {
            case UPSC -> 1000000; case SSC -> 2500000;
            case BANKING -> 3000000; case STATE_GOV -> 500000;
        };
    }
}
