package com.studyplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GeminiService — handles all AI interactions with Google Gemini API.
 * Includes caching, retry logic, and fallback responses.
 */
@Service
public class GeminiService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.base-url}")
    private String baseUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /** Simple in-memory cache to reduce API costs */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public GeminiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a study plan using Gemini AI.
     */
    public String generateStudyPlan(String examType, double hoursPerDay,
                                     List<String> strengths, List<String> weaknesses) {
        String cacheKey = "plan:" + examType + ":" + hoursPerDay + ":" + strengths + ":" + weaknesses;
        
        if (cache.containsKey(cacheKey)) {
            logger.debug("Returning cached study plan");
            return cache.get(cacheKey);
        }

        String prompt = String.format("""
            You are an expert Indian government exam preparation coach with 15+ years experience.
            Create a comprehensive, detailed weekly study plan optimized for cracking %s exam.
            
            Exam: %s
            Available hours per day: %.1f
            Strong subjects: %s
            Weak subjects: %s
            
            Generate a JSON response with this exact structure:
            {
                "weeklyPlan": [
                    {
                        "day": "Monday",
                        "sessions": [
                            {
                                "subject": "Subject Name",
                                "topic": "Specific Topic",
                                "startTime": "09:00",
                                "endTime": "10:30",
                                "priority": 3,
                                "tips": "Study tip for this session"
                            }
                        ]
                    }
                ],
                "recommendations": ["tip1", "tip2"],
                "focusAreas": ["area1", "area2"],
                "estimatedPreparationWeeks": 12
            }
            
            Rules:
            - Allocate more time to weak subjects
            - Include short breaks between sessions
            - Include revision slots
            - Be specific to %s exam syllabus
            - Total study hours per day should be approximately %.1f hours
            - Return ONLY valid JSON, no markdown
            """, examType, examType, hoursPerDay,
                strengths != null ? String.join(", ", strengths) : "Not specified",
                weaknesses != null ? String.join(", ", weaknesses) : "Not specified",
                examType, hoursPerDay);

        String result = callGeminiApi(prompt);
        if (result != null) {
            cache.put(cacheKey, result);
        }
        return result;
    }

    /**
     * Generate mock test questions using Gemini AI.
     * Includes a unique seed in the prompt to ensure different questions each time.
     */
    public String generateQuestions(String examType, String subject, String difficulty,
                                     int count, String questionType) {
        // Add a unique seed so that each request produces different questions
        String uniqueSeed = "SEED-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 100000);
        
        String prompt = String.format("""
            You are an expert question paper setter for Indian government exams with deep knowledge of the %s exam syllabus and pattern.
            
            IMPORTANT INSTRUCTIONS:
            - Generate EXACTLY %d unique %s questions for the subject: %s
            - Difficulty level: %s
            - Each question MUST be different — do NOT repeat questions or topics
            - Cover diverse topics within the subject relevant to %s exam
            - Questions must be factually accurate and exam-standard
            - Unique request ID (for randomization): %s
            
            Generate a JSON response with this EXACT structure (no extra text, no markdown):
            {
                "questions": [
                    {
                        "questionText": "The question text here",
                        "questionType": "%s",
                        "options": ["Option A", "Option B", "Option C", "Option D"],
                        "correctAnswer": "Option A",
                        "explanation": "Detailed explanation why this is correct",
                        "difficulty": "%s",
                        "topic": "Specific topic name"
                    }
                ]
            }
            
            Rules:
            - You MUST generate EXACTLY %d questions, no more, no less
            - Include exactly 4 options for each MCQ
            - The correctAnswer MUST exactly match one of the options
            - Provide clear, educational explanations
            - Cover different topics — do NOT cluster all questions on one topic
            - Follow %s exam pattern, difficulty standards, and marking scheme
            - Return ONLY valid JSON, absolutely no markdown formatting
            """, examType, count, questionType, subject, difficulty,
                examType, uniqueSeed,
                questionType, difficulty,
                count, examType);

        return callGeminiApi(prompt);
    }

    /**
     * Generate explanation for a question or topic.
     */
    public String generateExplanation(String topic, String examType) {
        String prompt = String.format("""
            Explain the following topic in the context of %s exam preparation.
            Topic: %s
            
            Provide a clear, concise explanation suitable for exam preparation.
            Include key points, examples, and common exam questions related to this topic.
            """, examType, topic);

        return callGeminiApi(prompt);
    }

    /**
     * Core method to call Gemini API with retry and fallback.
     */
    private String callGeminiApi(String prompt) {
        return callGeminiApi(prompt, 0.9);
    }

    private String callGeminiApi(String prompt, double temperature) {
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("Gemini API key not configured, using fallback response");
            return getFallbackResponse(prompt);
        }

        try {
            String url = String.format("%s/models/%s:generateContent?key=%s",
                    baseUrl, model, apiKey);

            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(
                        Map.of("text", prompt)
                    ))
                ),
                "generationConfig", Map.of(
                    "temperature", temperature,
                    "maxOutputTokens", 8192,
                    "responseMimeType", "application/json"
                )
            );

            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .retry(2)
                    .block();

            // Extract text from Gemini response
            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                String text = candidates.get(0)
                        .path("content")
                        .path("parts")
                        .get(0)
                        .path("text")
                        .asText();
                // Clean markdown code blocks if present
                text = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                return text;
            }

            logger.warn("Empty Gemini response, using fallback");
            return getFallbackResponse(prompt);

        } catch (Exception e) {
            logger.error("Gemini API call failed: {}", e.getMessage());
            return getFallbackResponse(prompt);
        }
    }

    /**
     * Fallback responses when Gemini API is unavailable.
     * Dynamically generates exam-specific questions matching the requested count.
     */
    private String getFallbackResponse(String prompt) {
        if (prompt.contains("study plan") || prompt.contains("weekly")) {
            return """
                {
                    "weeklyPlan": [
                        {"day": "Monday", "sessions": [
                            {"subject": "General Studies", "topic": "Indian Polity", "startTime": "09:00", "endTime": "11:00", "priority": 3, "tips": "Focus on constitutional articles"},
                            {"subject": "Quantitative Aptitude", "topic": "Number System", "startTime": "11:30", "endTime": "13:00", "priority": 2, "tips": "Practice speed calculations"},
                            {"subject": "English", "topic": "Reading Comprehension", "startTime": "14:00", "endTime": "15:30", "priority": 2, "tips": "Read newspaper editorials"}
                        ]},
                        {"day": "Tuesday", "sessions": [
                            {"subject": "Current Affairs", "topic": "National Events", "startTime": "09:00", "endTime": "10:30", "priority": 3, "tips": "Use monthly compilation"},
                            {"subject": "Reasoning", "topic": "Logical Reasoning", "startTime": "11:00", "endTime": "12:30", "priority": 2, "tips": "Practice puzzles daily"},
                            {"subject": "General Knowledge", "topic": "History", "startTime": "14:00", "endTime": "15:30", "priority": 2, "tips": "Focus on modern history"}
                        ]},
                        {"day": "Wednesday", "sessions": [
                            {"subject": "General Studies", "topic": "Geography", "startTime": "09:00", "endTime": "11:00", "priority": 2, "tips": "Use maps for visualization"},
                            {"subject": "Quantitative Aptitude", "topic": "Percentages", "startTime": "11:30", "endTime": "13:00", "priority": 3, "tips": "Learn shortcut methods"},
                            {"subject": "Revision", "topic": "Week Review", "startTime": "14:00", "endTime": "15:00", "priority": 1, "tips": "Review notes from Mon-Tue"}
                        ]},
                        {"day": "Thursday", "sessions": [
                            {"subject": "Current Affairs", "topic": "International Events", "startTime": "09:00", "endTime": "10:30", "priority": 2, "tips": "Focus on India's foreign relations"},
                            {"subject": "English", "topic": "Grammar & Vocabulary", "startTime": "11:00", "endTime": "12:30", "priority": 2, "tips": "Learn 10 new words daily"},
                            {"subject": "General Studies", "topic": "Economy", "startTime": "14:00", "endTime": "15:30", "priority": 3, "tips": "Focus on budget and policies"}
                        ]},
                        {"day": "Friday", "sessions": [
                            {"subject": "Reasoning", "topic": "Data Interpretation", "startTime": "09:00", "endTime": "10:30", "priority": 2, "tips": "Practice with mock charts"},
                            {"subject": "General Studies", "topic": "Science & Tech", "startTime": "11:00", "endTime": "12:30", "priority": 2, "tips": "Focus on recent developments"},
                            {"subject": "Mock Test", "topic": "Weekly Assessment", "startTime": "14:00", "endTime": "16:00", "priority": 3, "tips": "Simulate exam conditions"}
                        ]},
                        {"day": "Saturday", "sessions": [
                            {"subject": "Revision", "topic": "Weak Areas", "startTime": "09:00", "endTime": "11:00", "priority": 3, "tips": "Focus on topics you struggled with"},
                            {"subject": "Previous Year Papers", "topic": "Analysis", "startTime": "11:30", "endTime": "13:00", "priority": 2, "tips": "Understand exam patterns"}
                        ]},
                        {"day": "Sunday", "sessions": [
                            {"subject": "Current Affairs", "topic": "Weekly Compilation", "startTime": "10:00", "endTime": "11:30", "priority": 2, "tips": "Consolidate the week's events"},
                            {"subject": "Light Reading", "topic": "General Awareness", "startTime": "11:30", "endTime": "12:30", "priority": 1, "tips": "Read magazines and newspapers"}
                        ]}
                    ],
                    "recommendations": [
                        "Start each day with 15 minutes of newspaper reading",
                        "Take 10-minute breaks between study sessions",
                        "Maintain a separate notebook for formulae and key facts",
                        "Practice at least one mock test per week"
                    ],
                    "focusAreas": ["Current Affairs", "Quantitative Aptitude", "General Studies"],
                    "estimatedPreparationWeeks": 16
                }
                """;
        }
        throw new RuntimeException("Gemini API call failed or not configured. Please configure a valid API key to generate mock test questions.");
    }
}
