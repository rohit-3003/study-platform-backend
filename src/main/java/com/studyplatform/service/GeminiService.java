package com.studyplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
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
        return buildFallbackQuestions(prompt);
    }

    /**
     * Build dynamic fallback questions based on exam type and requested count.
     * Uses a large question bank and randomly selects questions.
     */
    private String buildFallbackQuestions(String prompt) {
        // Determine how many questions were requested (parse from prompt)
        int requestedCount = 10;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("EXACTLY (\\d+) unique").matcher(prompt);
            if (m.find()) requestedCount = Integer.parseInt(m.group(1));
        } catch (Exception ignored) {}

        // Determine exam type from prompt
        String examType = "UPSC";
        if (prompt.toUpperCase().contains("SSC")) examType = "SSC";
        else if (prompt.toUpperCase().contains("BANKING")) examType = "BANKING";
        else if (prompt.toUpperCase().contains("STATE_GOV")) examType = "STATE_GOV";

        // Large question bank per exam type
        List<String> questionPool = getQuestionPool(examType);

        // Shuffle and pick the right number
        java.util.Collections.shuffle(questionPool);
        int count = Math.min(requestedCount, questionPool.size());

        StringBuilder sb = new StringBuilder();
        sb.append("{\"questions\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            sb.append(questionPool.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    private List<String> getQuestionPool(String examType) {
        List<String> pool = new ArrayList<>();
        switch (examType) {
            case "SSC":
                pool.add("{\"questionText\":\"Who is known as the Father of the Indian Constitution?\",\"questionType\":\"MCQ\",\"options\":[\"Mahatma Gandhi\",\"Dr. B.R. Ambedkar\",\"Jawaharlal Nehru\",\"Sardar Patel\"],\"correctAnswer\":\"Dr. B.R. Ambedkar\",\"explanation\":\"Dr. B.R. Ambedkar was the chairman of the Drafting Committee of the Indian Constitution.\",\"difficulty\":\"EASY\",\"topic\":\"Indian Polity\"}");
                pool.add("{\"questionText\":\"Which gas is most abundant in Earth's atmosphere?\",\"questionType\":\"MCQ\",\"options\":[\"Oxygen\",\"Carbon Dioxide\",\"Nitrogen\",\"Helium\"],\"correctAnswer\":\"Nitrogen\",\"explanation\":\"Nitrogen makes up about 78% of Earth's atmosphere.\",\"difficulty\":\"EASY\",\"topic\":\"General Science\"}");
                pool.add("{\"questionText\":\"What is the SI unit of electric current?\",\"questionType\":\"MCQ\",\"options\":[\"Volt\",\"Ampere\",\"Ohm\",\"Watt\"],\"correctAnswer\":\"Ampere\",\"explanation\":\"The SI unit of electric current is the Ampere, named after André-Marie Ampère.\",\"difficulty\":\"EASY\",\"topic\":\"Physics\"}");
                pool.add("{\"questionText\":\"The Battle of Plassey was fought in which year?\",\"questionType\":\"MCQ\",\"options\":[\"1757\",\"1764\",\"1857\",\"1947\"],\"correctAnswer\":\"1757\",\"explanation\":\"The Battle of Plassey was fought on 23 June 1757, between the British East India Company and the Nawab of Bengal.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian History\"}");
                pool.add("{\"questionText\":\"Which planet is known as the Red Planet?\",\"questionType\":\"MCQ\",\"options\":[\"Venus\",\"Jupiter\",\"Mars\",\"Saturn\"],\"correctAnswer\":\"Mars\",\"explanation\":\"Mars appears red due to iron oxide (rust) on its surface.\",\"difficulty\":\"EASY\",\"topic\":\"General Science\"}");
                pool.add("{\"questionText\":\"What is the chemical formula for common salt?\",\"questionType\":\"MCQ\",\"options\":[\"NaCl\",\"KCl\",\"CaCl2\",\"MgCl2\"],\"correctAnswer\":\"NaCl\",\"explanation\":\"Common salt is sodium chloride, represented as NaCl.\",\"difficulty\":\"EASY\",\"topic\":\"Chemistry\"}");
                pool.add("{\"questionText\":\"Who wrote the book 'Discovery of India'?\",\"questionType\":\"MCQ\",\"options\":[\"Mahatma Gandhi\",\"Jawaharlal Nehru\",\"Rabindranath Tagore\",\"Dr. S. Radhakrishnan\"],\"correctAnswer\":\"Jawaharlal Nehru\",\"explanation\":\"'The Discovery of India' was written by Jawaharlal Nehru during his imprisonment in 1944.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian History\"}");
                pool.add("{\"questionText\":\"What does LCM stand for in mathematics?\",\"questionType\":\"MCQ\",\"options\":[\"Least Common Multiple\",\"Lowest Common Measure\",\"Least Common Measure\",\"Lowest Common Multiple\"],\"correctAnswer\":\"Least Common Multiple\",\"explanation\":\"LCM stands for Least Common Multiple — the smallest positive integer divisible by both numbers.\",\"difficulty\":\"EASY\",\"topic\":\"Mathematics\"}");
                pool.add("{\"questionText\":\"Which Indian river is known as the 'Sorrow of Bihar'?\",\"questionType\":\"MCQ\",\"options\":[\"Ganga\",\"Kosi\",\"Son\",\"Gandak\"],\"correctAnswer\":\"Kosi\",\"explanation\":\"The Kosi River is called the 'Sorrow of Bihar' due to its frequent devastating floods.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian Geography\"}");
                pool.add("{\"questionText\":\"The Tropic of Cancer passes through how many Indian states?\",\"questionType\":\"MCQ\",\"options\":[\"6\",\"7\",\"8\",\"9\"],\"correctAnswer\":\"8\",\"explanation\":\"The Tropic of Cancer passes through 8 Indian states: Gujarat, Rajasthan, Madhya Pradesh, Chhattisgarh, Jharkhand, West Bengal, Tripura, and Mizoram.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian Geography\"}");
                pool.add("{\"questionText\":\"If a train runs at 60 km/hr, how long will it take to cover 240 km?\",\"questionType\":\"MCQ\",\"options\":[\"3 hours\",\"4 hours\",\"5 hours\",\"6 hours\"],\"correctAnswer\":\"4 hours\",\"explanation\":\"Time = Distance/Speed = 240/60 = 4 hours.\",\"difficulty\":\"EASY\",\"topic\":\"Quantitative Aptitude\"}");
                pool.add("{\"questionText\":\"Which vitamin is also known as ascorbic acid?\",\"questionType\":\"MCQ\",\"options\":[\"Vitamin A\",\"Vitamin B\",\"Vitamin C\",\"Vitamin D\"],\"correctAnswer\":\"Vitamin C\",\"explanation\":\"Vitamin C is chemically known as ascorbic acid and is essential for immune function.\",\"difficulty\":\"EASY\",\"topic\":\"General Science\"}");
                pool.add("{\"questionText\":\"The Quit India Movement was launched in which year?\",\"questionType\":\"MCQ\",\"options\":[\"1940\",\"1942\",\"1944\",\"1946\"],\"correctAnswer\":\"1942\",\"explanation\":\"The Quit India Movement was launched by Mahatma Gandhi on 8 August 1942 during World War II.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian History\"}");
                pool.add("{\"questionText\":\"What is the percentage of 150 out of 600?\",\"questionType\":\"MCQ\",\"options\":[\"20%\",\"25%\",\"30%\",\"35%\"],\"correctAnswer\":\"25%\",\"explanation\":\"Percentage = (150/600) × 100 = 25%.\",\"difficulty\":\"EASY\",\"topic\":\"Quantitative Aptitude\"}");
                pool.add("{\"questionText\":\"Which is the smallest continent by area?\",\"questionType\":\"MCQ\",\"options\":[\"Europe\",\"Antarctica\",\"Australia\",\"South America\"],\"correctAnswer\":\"Australia\",\"explanation\":\"Australia is the smallest continent, covering approximately 7.7 million square kilometers.\",\"difficulty\":\"EASY\",\"topic\":\"World Geography\"}");
                break;
            case "BANKING":
                pool.add("{\"questionText\":\"What does NEFT stand for?\",\"questionType\":\"MCQ\",\"options\":[\"National Electronic Fund Transfer\",\"New Electronic Fund Transfer\",\"National Exchange Fund Transfer\",\"Net Electronic Fund Transfer\"],\"correctAnswer\":\"National Electronic Fund Transfer\",\"explanation\":\"NEFT stands for National Electronic Fund Transfer, a payment system operated by RBI.\",\"difficulty\":\"EASY\",\"topic\":\"Banking Terms\"}");
                pool.add("{\"questionText\":\"The headquarters of Reserve Bank of India is located in?\",\"questionType\":\"MCQ\",\"options\":[\"New Delhi\",\"Mumbai\",\"Kolkata\",\"Chennai\"],\"correctAnswer\":\"Mumbai\",\"explanation\":\"The RBI headquarters is located in Mumbai, Maharashtra.\",\"difficulty\":\"EASY\",\"topic\":\"Banking Awareness\"}");
                pool.add("{\"questionText\":\"What is the full form of NBFC?\",\"questionType\":\"MCQ\",\"options\":[\"National Banking and Finance Corporation\",\"Non-Banking Financial Company\",\"National Bank of Financial Cooperation\",\"Non-Business Financial Company\"],\"correctAnswer\":\"Non-Banking Financial Company\",\"explanation\":\"NBFC stands for Non-Banking Financial Company, registered under the Companies Act.\",\"difficulty\":\"EASY\",\"topic\":\"Banking Terms\"}");
                pool.add("{\"questionText\":\"What is the minimum age to open a savings account in India?\",\"questionType\":\"MCQ\",\"options\":[\"10 years\",\"12 years\",\"14 years\",\"18 years\"],\"correctAnswer\":\"10 years\",\"explanation\":\"A minor above 10 years can independently open and operate a savings account in India.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Banking Awareness\"}");
                pool.add("{\"questionText\":\"Which bank is the largest public sector bank in India?\",\"questionType\":\"MCQ\",\"options\":[\"Punjab National Bank\",\"Bank of Baroda\",\"State Bank of India\",\"Canara Bank\"],\"correctAnswer\":\"State Bank of India\",\"explanation\":\"SBI is the largest public sector bank in India in terms of assets and branches.\",\"difficulty\":\"EASY\",\"topic\":\"Banking Awareness\"}");
                pool.add("{\"questionText\":\"What does RTGS stand for?\",\"questionType\":\"MCQ\",\"options\":[\"Real Time Gross Settlement\",\"Real Time General Settlement\",\"Rapid Transfer Gross Settlement\",\"Real Transaction Gross Settlement\"],\"correctAnswer\":\"Real Time Gross Settlement\",\"explanation\":\"RTGS is a real-time, gross settlement funds transfer system for high-value transactions.\",\"difficulty\":\"EASY\",\"topic\":\"Banking Terms\"}");
                pool.add("{\"questionText\":\"Who regulates the banking sector in India?\",\"questionType\":\"MCQ\",\"options\":[\"SEBI\",\"RBI\",\"IRDAI\",\"Ministry of Finance\"],\"correctAnswer\":\"RBI\",\"explanation\":\"The Reserve Bank of India (RBI) is the central bank that regulates the banking sector.\",\"difficulty\":\"EASY\",\"topic\":\"Banking Regulation\"}");
                pool.add("{\"questionText\":\"What is the repo rate?\",\"questionType\":\"MCQ\",\"options\":[\"Rate at which banks borrow from RBI\",\"Rate at which RBI borrows from banks\",\"Interest rate on savings accounts\",\"Rate of inflation\"],\"correctAnswer\":\"Rate at which banks borrow from RBI\",\"explanation\":\"Repo rate is the rate at which commercial banks borrow money from the RBI by selling securities.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Monetary Policy\"}");
                pool.add("{\"questionText\":\"Which of the following is NOT a function of RBI?\",\"questionType\":\"MCQ\",\"options\":[\"Issuing currency notes\",\"Banker to the government\",\"Regulating stock markets\",\"Managing foreign exchange\"],\"correctAnswer\":\"Regulating stock markets\",\"explanation\":\"Regulating stock markets is the function of SEBI, not RBI.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Banking Regulation\"}");
                pool.add("{\"questionText\":\"What is the full form of KYC?\",\"questionType\":\"MCQ\",\"options\":[\"Know Your Customer\",\"Keep Your Card\",\"Know Your Credit\",\"Key Year Calculation\"],\"correctAnswer\":\"Know Your Customer\",\"explanation\":\"KYC stands for Know Your Customer, a verification process used by banks.\",\"difficulty\":\"EASY\",\"topic\":\"Banking Terms\"}");
                pool.add("{\"questionText\":\"What is CRR in banking?\",\"questionType\":\"MCQ\",\"options\":[\"Cash Reserve Ratio\",\"Credit Reserve Ratio\",\"Central Reserve Ratio\",\"Current Reserve Ratio\"],\"correctAnswer\":\"Cash Reserve Ratio\",\"explanation\":\"CRR is the percentage of total deposits that banks must hold as reserves with the RBI.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Monetary Policy\"}");
                pool.add("{\"questionText\":\"The Pradhan Mantri Jan Dhan Yojana was launched in which year?\",\"questionType\":\"MCQ\",\"options\":[\"2012\",\"2014\",\"2015\",\"2016\"],\"correctAnswer\":\"2014\",\"explanation\":\"PMJDY was launched on 28 August 2014 for financial inclusion.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Government Schemes\"}");
                pool.add("{\"questionText\":\"What is SLR in banking terms?\",\"questionType\":\"MCQ\",\"options\":[\"Statutory Liquidity Ratio\",\"Standard Lending Rate\",\"Savings and Liquidity Ratio\",\"Standard Liquidity Rate\"],\"correctAnswer\":\"Statutory Liquidity Ratio\",\"explanation\":\"SLR is the percentage of deposits banks must maintain in gold, cash, or government securities.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Monetary Policy\"}");
                pool.add("{\"questionText\":\"Which committee recommended 14 bank nationalization in 1969?\",\"questionType\":\"MCQ\",\"options\":[\"Narasimham Committee\",\"Goswami Committee\",\"P.C. Mahalanobis Committee\",\"None – it was a political decision\"],\"correctAnswer\":\"None – it was a political decision\",\"explanation\":\"The nationalization of 14 banks in 1969 was a political decision by PM Indira Gandhi.\",\"difficulty\":\"HARD\",\"topic\":\"Banking History\"}");
                pool.add("{\"questionText\":\"What is a demand draft?\",\"questionType\":\"MCQ\",\"options\":[\"A cheque issued by a bank\",\"A prepaid negotiable instrument\",\"An online transaction mode\",\"A loan sanction letter\"],\"correctAnswer\":\"A prepaid negotiable instrument\",\"explanation\":\"A demand draft is a prepaid negotiable instrument where the amount is paid beforehand.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Banking Instruments\"}");
                break;
            default: // UPSC and STATE_GOV
                pool.add("{\"questionText\":\"Which article of the Indian Constitution deals with the Right to Equality?\",\"questionType\":\"MCQ\",\"options\":[\"Article 12\",\"Article 14\",\"Article 19\",\"Article 21\"],\"correctAnswer\":\"Article 14\",\"explanation\":\"Article 14 guarantees equality before the law and equal protection of the laws.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Fundamental Rights\"}");
                pool.add("{\"questionText\":\"The Reserve Bank of India was established in which year?\",\"questionType\":\"MCQ\",\"options\":[\"1935\",\"1947\",\"1950\",\"1969\"],\"correctAnswer\":\"1935\",\"explanation\":\"RBI was established on April 1, 1935, based on the Hilton Young Commission recommendations.\",\"difficulty\":\"EASY\",\"topic\":\"Indian Economy\"}");
                pool.add("{\"questionText\":\"Which of the following rivers does NOT originate in India?\",\"questionType\":\"MCQ\",\"options\":[\"Ganga\",\"Brahmaputra\",\"Godavari\",\"Krishna\"],\"correctAnswer\":\"Brahmaputra\",\"explanation\":\"Brahmaputra originates near Lake Mansarovar in Tibet (China).\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian Geography\"}");
                pool.add("{\"questionText\":\"Who was the first President of India?\",\"questionType\":\"MCQ\",\"options\":[\"Dr. Rajendra Prasad\",\"Dr. S. Radhakrishnan\",\"Jawaharlal Nehru\",\"Dr. B.R. Ambedkar\"],\"correctAnswer\":\"Dr. Rajendra Prasad\",\"explanation\":\"Dr. Rajendra Prasad served as the first President of India from 1950 to 1962.\",\"difficulty\":\"EASY\",\"topic\":\"Indian Polity\"}");
                pool.add("{\"questionText\":\"The Panchayati Raj System was introduced through which constitutional amendment?\",\"questionType\":\"MCQ\",\"options\":[\"42nd Amendment\",\"44th Amendment\",\"73rd Amendment\",\"74th Amendment\"],\"correctAnswer\":\"73rd Amendment\",\"explanation\":\"The 73rd Constitutional Amendment Act of 1992 gave constitutional status to Panchayati Raj.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian Polity\"}");
                pool.add("{\"questionText\":\"Which Five-Year Plan gave the highest priority to agriculture?\",\"questionType\":\"MCQ\",\"options\":[\"First\",\"Second\",\"Third\",\"Fourth\"],\"correctAnswer\":\"First\",\"explanation\":\"The First Five-Year Plan (1951-56) focused primarily on agriculture and irrigation.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian Economy\"}");
                pool.add("{\"questionText\":\"The Indian National Congress was founded in which year?\",\"questionType\":\"MCQ\",\"options\":[\"1857\",\"1885\",\"1905\",\"1920\"],\"correctAnswer\":\"1885\",\"explanation\":\"The INC was founded in 1885 by Allan Octavian Hume in Bombay.\",\"difficulty\":\"EASY\",\"topic\":\"Modern Indian History\"}");
                pool.add("{\"questionText\":\"Which Article of the Constitution provides for the Election Commission?\",\"questionType\":\"MCQ\",\"options\":[\"Article 280\",\"Article 312\",\"Article 324\",\"Article 356\"],\"correctAnswer\":\"Article 324\",\"explanation\":\"Article 324 vests the superintendence, direction, and control of elections in the Election Commission.\",\"difficulty\":\"HARD\",\"topic\":\"Indian Polity\"}");
                pool.add("{\"questionText\":\"The Chipko Movement was related to conservation of?\",\"questionType\":\"MCQ\",\"options\":[\"Soil\",\"Water\",\"Forests\",\"Wildlife\"],\"correctAnswer\":\"Forests\",\"explanation\":\"The Chipko Movement (1973) was a forest conservation movement in Uttarakhand.\",\"difficulty\":\"EASY\",\"topic\":\"Environment\"}");
                pool.add("{\"questionText\":\"What is the tenure of a Rajya Sabha member?\",\"questionType\":\"MCQ\",\"options\":[\"4 years\",\"5 years\",\"6 years\",\"Permanent\"],\"correctAnswer\":\"6 years\",\"explanation\":\"Members of Rajya Sabha serve a term of 6 years, with one-third retiring every 2 years.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian Polity\"}");
                pool.add("{\"questionText\":\"The Tropic of Cancer passes through which Indian state?\",\"questionType\":\"MCQ\",\"options\":[\"Uttar Pradesh\",\"Madhya Pradesh\",\"Maharashtra\",\"Karnataka\"],\"correctAnswer\":\"Madhya Pradesh\",\"explanation\":\"The Tropic of Cancer passes through Madhya Pradesh along with 7 other states.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian Geography\"}");
                pool.add("{\"questionText\":\"Who is regarded as the father of India's Green Revolution?\",\"questionType\":\"MCQ\",\"options\":[\"Verghese Kurien\",\"M.S. Swaminathan\",\"Norman Borlaug\",\"C. Subramaniam\"],\"correctAnswer\":\"M.S. Swaminathan\",\"explanation\":\"M.S. Swaminathan is known as the father of India's Green Revolution for introducing high-yield crops.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Agriculture\"}");
                pool.add("{\"questionText\":\"The Minimum Support Price (MSP) is announced by?\",\"questionType\":\"MCQ\",\"options\":[\"RBI\",\"NABARD\",\"Government of India\",\"State Governments\"],\"correctAnswer\":\"Government of India\",\"explanation\":\"MSP is announced by the Government of India on the recommendation of CACP.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian Economy\"}");
                pool.add("{\"questionText\":\"Fundamental Duties are mentioned in which Part of the Constitution?\",\"questionType\":\"MCQ\",\"options\":[\"Part III\",\"Part IV\",\"Part IVA\",\"Part V\"],\"correctAnswer\":\"Part IVA\",\"explanation\":\"Fundamental Duties were added by the 42nd Amendment in 1976 under Part IVA (Article 51A).\",\"difficulty\":\"HARD\",\"topic\":\"Indian Polity\"}");
                pool.add("{\"questionText\":\"Which Indian state has the longest coastline?\",\"questionType\":\"MCQ\",\"options\":[\"Tamil Nadu\",\"Gujarat\",\"Maharashtra\",\"Kerala\"],\"correctAnswer\":\"Gujarat\",\"explanation\":\"Gujarat has the longest coastline among Indian states at approximately 1,600 km.\",\"difficulty\":\"MEDIUM\",\"topic\":\"Indian Geography\"}");
                break;
        }
        return pool;
    }
}
