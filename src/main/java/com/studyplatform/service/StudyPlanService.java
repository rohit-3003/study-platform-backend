package com.studyplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.StudyDto;
import com.studyplatform.entity.*;
import com.studyplatform.exception.GlobalExceptionHandler.*;
import com.studyplatform.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * StudyPlanService — generates and manages AI-powered study plans.
 */
@Service
public class StudyPlanService {
    private static final Logger logger = LoggerFactory.getLogger(StudyPlanService.class);

    private final StudyPlanRepository studyPlanRepo;
    private final GeminiService geminiService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public StudyPlanService(StudyPlanRepository studyPlanRepo,
                            GeminiService geminiService, AuthService authService,
                            ObjectMapper objectMapper) {
        this.studyPlanRepo = studyPlanRepo;
        this.geminiService = geminiService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    /** Create a new AI-generated study plan */
    @Transactional
    public StudyDto.StudyPlanResponse createStudyPlan(StudyDto.CreateStudyPlanRequest request) {
        User user = authService.getCurrentUser();

        // Deactivate previous plans
        studyPlanRepo.findByUserIdAndIsActiveTrue(user.getId())
                .ifPresent(plan -> {
                    plan.setActive(false);
                    studyPlanRepo.save(plan);
                });

        // Generate plan using Gemini AI
        String planJson = geminiService.generateStudyPlan(
                request.getExamType().name(),
                request.getAvailableHoursPerDay(),
                request.getStrengths(),
                request.getWeaknesses()
        );

        StudyPlan plan = StudyPlan.builder()
                .user(user)
                .examType(request.getExamType())
                .availableHoursPerDay(request.getAvailableHoursPerDay())
                .strengths(request.getStrengths() != null ? String.join(",", request.getStrengths()) : null)
                .weaknesses(request.getWeaknesses() != null ? String.join(",", request.getWeaknesses()) : null)
                .planData(planJson)
                .isActive(true)
                .build();

        // Parse AI response and create entries
        try {
            JsonNode root = objectMapper.readTree(planJson);
            JsonNode weeklyPlan = root.path("weeklyPlan");
            if (weeklyPlan.isArray()) {
                int dayIndex = 0;
                for (JsonNode dayNode : weeklyPlan) {
                    JsonNode sessions = dayNode.path("sessions");
                    if (sessions.isArray()) {
                        for (JsonNode session : sessions) {
                            StudyPlanEntry entry = StudyPlanEntry.builder()
                                    .studyPlan(plan)
                                    .dayOfWeek(dayIndex)
                                    .subject(session.path("subject").asText())
                                    .topic(session.path("topic").asText())
                                    .startTime(parseTime(session.path("startTime").asText()))
                                    .endTime(parseTime(session.path("endTime").asText()))
                                    .priority(session.path("priority").asInt(2))
                                    .build();
                            plan.getEntries().add(entry);
                        }
                    }
                    dayIndex++;
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse AI plan entries: {}", e.getMessage());
        }

        studyPlanRepo.save(plan);
        logger.info("Study plan created for user: {}", user.getEmail());

        return mapToResponse(plan);
    }

    /** Get user's active study plan */
    public StudyDto.StudyPlanResponse getActivePlan() {
        User user = authService.getCurrentUser();
        return studyPlanRepo.findByUserIdAndIsActiveTrue(user.getId())
                .map(this::mapToResponse)
                .orElse(null);
    }

    /** Get all study plans for the current user */
    public List<StudyDto.StudyPlanResponse> getAllPlans() {
        User user = authService.getCurrentUser();
        return studyPlanRepo.findByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /** Mark a study plan entry as completed */
    @Transactional
    public void markEntryCompleted(Long entryId) {
        User user = authService.getCurrentUser();
        StudyPlan plan = studyPlanRepo.findByUserIdAndIsActiveTrue(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active plan"));
        
        plan.getEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .ifPresent(entry -> entry.setCompleted(true));
        
        studyPlanRepo.save(plan);
    }

    private StudyDto.StudyPlanResponse mapToResponse(StudyPlan plan) {
        Object planData = null;
        try {
            planData = objectMapper.readTree(plan.getPlanData());
        } catch (Exception e) {
            planData = plan.getPlanData();
        }

        List<StudyDto.StudyPlanEntryDto> entries = plan.getEntries().stream()
                .map(e -> StudyDto.StudyPlanEntryDto.builder()
                        .id(e.getId())
                        .dayOfWeek(e.getDayOfWeek())
                        .subject(e.getSubject())
                        .topic(e.getTopic())
                        .startTime(e.getStartTime() != null ? e.getStartTime().toString() : null)
                        .endTime(e.getEndTime() != null ? e.getEndTime().toString() : null)
                        .priority(e.getPriority())
                        .isCompleted(e.isCompleted())
                        .build())
                .collect(Collectors.toList());

        return StudyDto.StudyPlanResponse.builder()
                .id(plan.getId())
                .examType(plan.getExamType().name())
                .availableHoursPerDay(plan.getAvailableHoursPerDay())
                .strengths(plan.getStrengths() != null ?
                        Arrays.asList(plan.getStrengths().split(",")) : null)
                .weaknesses(plan.getWeaknesses() != null ?
                        Arrays.asList(plan.getWeaknesses().split(",")) : null)
                .planData(planData)
                .entries(entries)
                .isActive(plan.isActive())
                .createdAt(plan.getCreatedAt().toString())
                .build();
    }

    private LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return null;
        }
    }
}
