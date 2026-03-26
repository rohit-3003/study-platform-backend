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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MockTestService — handles mock test creation, AI question generation, and scoring.
 */
@Service
public class MockTestService {
    private static final Logger logger = LoggerFactory.getLogger(MockTestService.class);

    private final MockTestRepository mockTestRepo;
    private final QuestionRepository questionRepo;
    private final TestResultRepository resultRepo;
    private final SubjectRepository subjectRepo;
    private final GeminiService geminiService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public MockTestService(MockTestRepository mockTestRepo, QuestionRepository questionRepo,
                           TestResultRepository resultRepo, SubjectRepository subjectRepo,
                           GeminiService geminiService, AuthService authService,
                           ObjectMapper objectMapper) {
        this.mockTestRepo = mockTestRepo;
        this.questionRepo = questionRepo;
        this.resultRepo = resultRepo;
        this.subjectRepo = subjectRepo;
        this.geminiService = geminiService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    /** Create a new mock test with AI-generated questions */
    @Transactional
    public StudyDto.MockTestResponse createMockTest(StudyDto.CreateMockTestRequest request) {
        User user = authService.getCurrentUser();

        // Get subject name if provided
        String subjectName = "General Studies";
        if (request.getSubjectId() != null) {
            subjectName = subjectRepo.findById(request.getSubjectId())
                    .map(Subject::getName)
                    .orElse("General Studies");
        }

        // Generate questions using Gemini AI
        String questionsJson = geminiService.generateQuestions(
                request.getExamType().name(),
                subjectName,
                request.getDifficulty().name(),
                request.getTotalQuestions(),
                "MCQ"
        );

        // Create mock test
        MockTest test = MockTest.builder()
                .user(user)
                .examType(request.getExamType())
                .title(request.getExamType().name() + " Mock Test - " + subjectName)
                .difficulty(request.getDifficulty())
                .totalQuestions(request.getTotalQuestions())
                .durationMinutes(request.getDurationMinutes())
                .status(MockTest.TestStatus.CREATED)
                .build();

        // Parse AI questions and create Question + MockTestQuestion entities
        try {
            JsonNode root = objectMapper.readTree(questionsJson);
            JsonNode questionsArray = root.path("questions");
            if (questionsArray.isArray()) {
                int order = 1;
                for (JsonNode qNode : questionsArray) {
                    Question question = Question.builder()
                            .examType(request.getExamType())
                            .questionText(qNode.path("questionText").asText())
                            .questionType(Question.QuestionType.MCQ)
                            .options(qNode.path("options").toString())
                            .correctAnswer(qNode.path("correctAnswer").asText())
                            .explanation(qNode.path("explanation").asText())
                            .difficulty(request.getDifficulty())
                            .isAiGenerated(true)
                            .build();
                    questionRepo.save(question);

                    MockTestQuestion mtq = MockTestQuestion.builder()
                            .mockTest(test)
                            .question(question)
                            .questionOrder(order++)
                            .build();
                    test.getQuestions().add(mtq);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse AI questions: {}", e.getMessage());
            throw new BusinessException("Failed to generate questions. Please try again.");
        }

        // Update actual total based on successfully parsed questions
        test.setTotalQuestions(test.getQuestions().size());
        mockTestRepo.save(test);
        logger.info("Mock test created: {} for user: {} with {} questions", test.getTitle(), user.getEmail(), test.getQuestions().size());

        return mapToResponse(test, false);
    }

    /** Get test details (without answers during active test) */
    public StudyDto.MockTestResponse getTest(Long testId) {
        User user = authService.getCurrentUser();
        MockTest test = mockTestRepo.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test not found"));

        if (!test.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You don't have access to this test");
        }

        boolean showAnswers = test.getStatus() == MockTest.TestStatus.COMPLETED;
        return mapToResponse(test, showAnswers);
    }

    /** Submit test answers and calculate score */
    @Transactional
    public StudyDto.TestResultResponse submitTest(StudyDto.SubmitTestRequest request) {
        User user = authService.getCurrentUser();
        MockTest test = mockTestRepo.findById(request.getTestId())
                .orElseThrow(() -> new ResourceNotFoundException("Test not found"));

        if (!test.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You don't have access to this test");
        }

        if (test.getStatus() == MockTest.TestStatus.COMPLETED) {
            throw new BusinessException("Test already submitted");
        }

        // Process answers and calculate score
        int score = 0;
        int totalMarks = test.getQuestions().size();
        if (totalMarks == 0) totalMarks = 1; // Prevent division by zero
        Map<String, int[]> subjectScores = new HashMap<>(); // subject -> [correct, total]

        for (StudyDto.AnswerSubmission answer : request.getAnswers()) {
            test.getQuestions().stream()
                    .filter(q -> q.getQuestion().getId().equals(answer.getQuestionId()))
                    .findFirst()
                    .ifPresent(mtq -> {
                        mtq.setUserAnswer(answer.getAnswer());
                        boolean isCorrect = mtq.getQuestion().getCorrectAnswer()
                                .equalsIgnoreCase(answer.getAnswer());
                        mtq.setIsCorrect(isCorrect);
                        mtq.setTimeSpentSeconds(answer.getTimeSpentSeconds());
                    });
        }

        // Calculate score
        for (MockTestQuestion mtq : test.getQuestions()) {
            if (Boolean.TRUE.equals(mtq.getIsCorrect())) {
                score++;
            }
        }

        test.setStatus(MockTest.TestStatus.COMPLETED);

        // Calculate percentile
        double percentage = (score * 100.0) / totalMarks;
        long belowCount = resultRepo.countBelowPercentage(percentage);
        long totalResults = resultRepo.countAllResults() + 1;
        double percentile = (belowCount * 100.0) / totalResults;

        // Save test result
        TestResult result = TestResult.builder()
                .mockTest(test)
                .user(user)
                .score(score)
                .totalMarks(totalMarks)
                .percentage(percentage)
                .timeTaken(request.getTimeTakenSeconds())
                .rankPercentile(percentile)
                .completedAt(LocalDateTime.now())
                .build();
        resultRepo.save(result);
        mockTestRepo.save(test);

        logger.info("Test submitted: score {}/{} for user: {}", score, totalMarks, user.getEmail());

        // Build detailed response
        List<StudyDto.QuestionWithAnswerDto> detailedResults = test.getQuestions().stream()
                .sorted(Comparator.comparing(MockTestQuestion::getQuestionOrder))
                .map(mtq -> {
                    List<String> options = parseOptions(mtq.getQuestion().getOptions());
                    return StudyDto.QuestionWithAnswerDto.builder()
                            .id(mtq.getQuestion().getId())
                            .questionText(mtq.getQuestion().getQuestionText())
                            .questionType(mtq.getQuestion().getQuestionType().name())
                            .options(options)
                            .correctAnswer(mtq.getQuestion().getCorrectAnswer())
                            .explanation(mtq.getQuestion().getExplanation())
                            .difficulty(mtq.getQuestion().getDifficulty().name())
                            .userAnswer(mtq.getUserAnswer())
                            .isCorrect(Boolean.TRUE.equals(mtq.getIsCorrect()))
                            .build();
                })
                .collect(Collectors.toList());

        return StudyDto.TestResultResponse.builder()
                .testId(test.getId())
                .score(score)
                .totalMarks(totalMarks)
                .percentage(percentage)
                .timeTaken(request.getTimeTakenSeconds())
                .rankPercentile(percentile)
                .detailedResults(detailedResults)
                .completedAt(result.getCompletedAt().toString())
                .build();
    }

    /** Get all tests for current user */
    public List<StudyDto.MockTestResponse> getUserTests() {
        User user = authService.getCurrentUser();
        return mockTestRepo.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(t -> mapToResponse(t, t.getStatus() == MockTest.TestStatus.COMPLETED))
                .collect(Collectors.toList());
    }

    // --- Private helpers ---

    private StudyDto.MockTestResponse mapToResponse(MockTest test, boolean includeAnswers) {
        List<StudyDto.QuestionDto> questions = test.getQuestions().stream()
                .sorted(Comparator.comparing(MockTestQuestion::getQuestionOrder))
                .map(mtq -> StudyDto.QuestionDto.builder()
                        .id(mtq.getQuestion().getId())
                        .questionText(mtq.getQuestion().getQuestionText())
                        .questionType(mtq.getQuestion().getQuestionType().name())
                        .options(parseOptions(mtq.getQuestion().getOptions()))
                        .difficulty(mtq.getQuestion().getDifficulty().name())
                        .build())
                .collect(Collectors.toList());

        return StudyDto.MockTestResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .examType(test.getExamType().name())
                .difficulty(test.getDifficulty().name())
                .totalQuestions(test.getTotalQuestions())
                .durationMinutes(test.getDurationMinutes())
                .status(test.getStatus().name())
                .questions(questions)
                .createdAt(test.getCreatedAt().toString())
                .build();
    }

    private List<String> parseOptions(String optionsJson) {
        try {
            JsonNode node = objectMapper.readTree(optionsJson);
            List<String> options = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(n -> options.add(n.asText()));
            }
            return options;
        } catch (Exception e) {
            return List.of();
        }
    }
}
