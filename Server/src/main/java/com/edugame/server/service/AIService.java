package com.edugame.server.service;

import com.edugame.server.model.Question;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AIService {

    private static String getApiUrl() {
        String apiKey = com.edugame.server.config.ConfigManager.getGeminiApiKey();
        String baseUrl = com.edugame.server.config.ConfigManager.get(
                "gemini.api.url",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-latest:generateContent"
        );

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }

        return baseUrl + "?key=" + apiKey;
    }
    /**
     * Tạo câu hỏi trắc nghiệm bằng Gemini AI
     */
    public static AIResult generateQuestions(String topic, String subject, String difficulty, int count) {
        List<Question> questions = new ArrayList<>();
        String errorMessage = null;

        try {
            System.out.println("🤖 [AI] Đang tạo " + count + " câu hỏi về chủ đề: " + topic);
            System.out.println("📝 [AI] Môn: " + subject + ", Độ khó: " + difficulty);

            // Kiểm tra cấu hình
            String configError = validateConfiguration();
            if (configError != null) {
                errorMessage = configError;
                return new AIResult(questions, errorMessage);
            }

            // Tạo prompt chi tiết
            String prompt = buildPrompt(topic, subject, difficulty, count);
            System.out.println("📤 [AI] Đang gửi request...");

            // Gửi request đến Gemini API
            String jsonBody = buildRequestBody(prompt);

            // Debug: Print JSON body để kiểm tra
            System.out.println("🔍 [AI] JSON Body Preview:");
            System.out.println(jsonBody.substring(0, Math.min(300, jsonBody.length())) + "...");

            HttpResponse<String> response = sendRequest(jsonBody);

            System.out.println("📥 [AI] Nhận response với status: " + response.statusCode());

            // Xử lý response
            if (response.statusCode() == 200) {
                questions = parseGeminiResponse(response.body(), subject, difficulty);

                if (questions.isEmpty()) {
                    errorMessage = "Không thể parse câu hỏi từ response!\n\n" +
                            "Nguyên nhân có thể:\n" +
                            "- AI trả về format không đúng\n" +
                            "- Prompt quá phức tạp\n\n" +
                            "Đề xuất:\n" +
                            "- Thử với số lượng ít hơn (5-10 câu)\n" +
                            "- Viết chủ đề rõ ràng hơn\n" +
                            "- Kiểm tra console log để xem chi tiết";
                } else {
                    System.out.println("✅ [AI] Đã tạo thành công " + questions.size() + " câu hỏi");
                }
            } else if (response.statusCode() == 401) {
                errorMessage = "❌ API Key không hợp lệ!\n\n" +
                        "API Key của bạn không được chấp nhận bởi Google.\n\n" +
                        "Giải pháp:\n" +
                        "1. Kiểm tra API Key có đúng không\n" +
                        "2. Lấy API Key mới từ: https://makersuite.google.com/app/apikey\n" +
                        "3. Cập nhật trong 'Cấu hình AI'";
                System.err.println("❌ [AI] 401 Unauthorized - API Key không hợp lệ");
            } else if (response.statusCode() == 429) {
                errorMessage = "❌ Đã vượt quá giới hạn API!\n\n" +
                        "Bạn đã sử dụng hết quota của Gemini API.\n\n" +
                        "Giải pháp:\n" +
                        "1. Đợi vài phút rồi thử lại\n" +
                        "2. Giới hạn miễn phí: 60 requests/phút, 1500/ngày\n" +
                        "3. Nâng cấp lên gói trả phí nếu cần nhiều hơn";
                System.err.println("❌ [AI] 429 Too Many Requests - Vượt quota");
            } else if (response.statusCode() == 400) {
                errorMessage = "❌ Request không hợp lệ!\n\n" +
                        "Có lỗi trong request gửi đến API.\n\n" +
                        "Chi tiết: " + extractErrorMessage(response.body());
                System.err.println("❌ [AI] 400 Bad Request");
                System.err.println("Response: " + response.body());
            } else {
                errorMessage = "❌ Lỗi API không xác định!\n\n" +
                        "HTTP Status: " + response.statusCode() + "\n" +
                        "Chi tiết: " + extractErrorMessage(response.body());
                System.err.println("❌ [AI] Lỗi API: " + response.statusCode());
                System.err.println("Response: " + response.body());
            }

        } catch (IllegalArgumentException e) {
            errorMessage = "❌ Lỗi cấu hình!\n\n" + e.getMessage() + "\n\n" +
                    "Vui lòng kiểm tra cấu hình API trong 'Cấu hình AI'";
            System.err.println("❌ [AI] Configuration error: " + e.getMessage());
        } catch (java.net.ConnectException e) {
            errorMessage = "❌ Không thể kết nối đến Gemini API!\n\n" +
                    "Nguyên nhân:\n" +
                    "- Không có kết nối internet\n" +
                    "- Firewall chặn kết nối\n" +
                    "- Google API đang bảo trì\n\n" +
                    "Vui lòng kiểm tra kết nối internet và thử lại.";
            System.err.println("❌ [AI] Connection error: " + e.getMessage());
        } catch (java.net.http.HttpTimeoutException e) {
            errorMessage = "❌ Request timeout!\n\n" +
                    "Kết nối quá chậm hoặc bị timeout.\n\n" +
                    "Đề xuất:\n" +
                    "- Kiểm tra kết nối internet\n" +
                    "- Giảm số lượng câu hỏi\n" +
                    "- Thử lại sau vài giây";
            System.err.println("❌ [AI] Timeout: " + e.getMessage());
        } catch (Exception e) {
            errorMessage = "❌ Lỗi không xác định!\n\n" +
                    "Chi tiết: " + e.getMessage() + "\n\n" +
                    "Vui lòng kiểm tra console log để biết thêm thông tin.";
            System.err.println("❌ [AI] Exception: " + e.getMessage());
            e.printStackTrace();
        }

        return new AIResult(questions, errorMessage);
    }

    /**
     * Validate cấu hình trước khi gọi API
     */
    private static String validateConfiguration() {
        try {
            if (!com.edugame.server.config.ConfigManager.isApiKeyConfigured()) {
                return "❌ API Key chưa được cấu hình!\n\n" +
                        "Vui lòng vào 'Cấu hình AI' để nhập API Key.\n\n" +
                        "Lấy API Key miễn phí tại:\n" +
                        "https://makersuite.google.com/app/apikey";
            }

            String apiKey = com.edugame.server.config.ConfigManager.getGeminiApiKey();
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "❌ API Key rỗng!\n\n" +
                        "Vui lòng cấu hình API Key hợp lệ.";
            }

            String apiUrl = getApiUrl();
            if (apiUrl == null || apiUrl.isEmpty()) {
                return "❌ API URL không hợp lệ!\n\n" +
                        "Vui lòng kiểm tra cấu hình.";
            }

            if (!apiUrl.startsWith("https://")) {
                return "❌ API URL phải bắt đầu bằng https://\n\n" +
                        "URL hiện tại: " + apiUrl + "\n\n" +
                        "Vui lòng kiểm tra file config.properties";
            }

            System.out.println("✅ [AI] Cấu hình hợp lệ");
            return null;

        } catch (Exception e) {
            return "❌ Lỗi kiểm tra cấu hình!\n\n" + e.getMessage();
        }
    }

    /**
     * Extract error message from API response
     */
    private static String extractErrorMessage(String responseBody) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(responseBody, JsonObject.class);
            if (root.has("error")) {
                JsonObject error = root.getAsJsonObject("error");
                if (error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return "Không có thông tin chi tiết";
    }

    /**
     * Class để trả về kết quả và lỗi
     */
    public static class AIResult {
        private List<Question> questions;
        private String errorMessage;

        public AIResult(List<Question> questions, String errorMessage) {
            this.questions = questions;
            this.errorMessage = errorMessage;
        }

        public List<Question> getQuestions() {
            return questions;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean hasError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }

        public boolean isSuccess() {
            return !questions.isEmpty() && !hasError();
        }
    }

    /**
     * Xây dựng prompt cho Gemini AI
     */
    private static String buildPrompt(String topic, String subject, String difficulty, int count) {
        String subjectVi = mapSubjectToVietnamese(subject);
        String difficultyVi = mapDifficultyToVietnamese(difficulty);

        return String.format(
                "Bạn là một giáo viên chuyên nghiệp . Hãy tạo %d câu hỏi trắc nghiệm phù hợp cho học sinh 6 đến 12 tuổi về môn %s về chủ đề '%s', độ khó '%s'.\n\n" +
                        "YÊU CẦU NGHIÊM NGẶT:\n" +
                        "1. Chỉ trả về một JSON Array thuần túy, KHÔNG có markdown (```json), KHÔNG có lời giải thích thêm\n" +
                        "2. Mỗi câu hỏi phải có cấu trúc JSON chính xác như sau:\n" +
                        "{\n" +
                        "  \"questionText\": \"Nội dung câu hỏi\",\n" +
                        "  \"optionA\": \"Đáp án A\",\n" +
                        "  \"optionB\": \"Đáp án B\",\n" +
                        "  \"optionC\": \"Đáp án C\",\n" +
                        "  \"optionD\": \"Đáp án D\",\n" +
                        "  \"correctAnswer\": \"A\"\n" +
                        "}\n\n" +
                        "3. correctAnswer chỉ được là một trong bốn giá trị: \"A\", \"B\", \"C\", hoặc \"D\"\n" +
                        "4. Câu hỏi phải phù hợp với độ khó %s:\n" +
                        "   - Dễ: Kiến thức cơ bản, dễ nhớ\n" +
                        "   - Trung bình: Cần suy luận, áp dụng kiến thức\n" +
                        "   - Khó: Cần phân tích sâu, tư duy phản biện\n" +
                        "5. Đáp án sai phải hợp lý, gây nhiễu nhưng có thể phân biệt được\n" +
                        "6. Nội dung phải đúng kiến thức, rõ ràng, không mơ hồ\n\n" +
                        "Bắt đầu JSON Array ngay bây giờ:",
                count, subjectVi, topic, difficultyVi, difficultyVi
        );
    }

    /**
     * Map môn học từ English sang Tiếng Việt
     */
    private static String mapSubjectToVietnamese(String subject) {
        switch (subject.toLowerCase()) {
            case "math": return "Toán học";
            case "literature": return "Ngữ văn";
            case "english": return "Tiếng Anh";
            default: return subject;
        }
    }

    /**
     * Map độ khó từ English sang Tiếng Việt
     */
    private static String mapDifficultyToVietnamese(String difficulty) {
        switch (difficulty.toLowerCase()) {
            case "easy": return "Dễ";
            case "medium": return "Trung bình";
            case "hard": return "Khó";
            default: return difficulty;
        }
    }

    /**
     * Xây dựng request body cho Gemini API
     * FIX: Sử dụng Gson thay vì String.format để tránh lỗi locale
     */
    private static String buildRequestBody(String prompt) {
        double temperature = com.edugame.server.config.ConfigManager.getAiTemperature();
        int maxTokens = com.edugame.server.config.ConfigManager.getMaxTokens();

        // FIX: Sử dụng Gson để build JSON - tránh vấn đề locale với số thực
        Gson gson = new Gson();
        JsonObject root = new JsonObject();

        // Contents array
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        root.add("contents", contents);

        // Generation config
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", temperature);
        generationConfig.addProperty("topK", 40);
        generationConfig.addProperty("topP", 0.95);
        generationConfig.addProperty("maxOutputTokens", maxTokens);
        root.add("generationConfig", generationConfig);

        return gson.toJson(root);
    }

    /**
     * Gửi HTTP request đến Gemini API
     */
    private static HttpResponse<String> sendRequest(String jsonBody) throws Exception {
        String apiUrl = getApiUrl();

        System.out.println("🌐 [AI] API URL: " + maskApiKey(apiUrl));
        System.out.println("📤 [AI] Request body length: " + jsonBody.length() + " characters");

        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new IllegalArgumentException("API URL is null or empty");
        }

        if (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
            throw new IllegalArgumentException("API URL must start with http:// or https://. Got: " + apiUrl);
        }

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Mask API Key trong URL để log an toàn
     */
    private static String maskApiKey(String url) {
        if (url == null) return "null";

        int keyIndex = url.indexOf("key=");
        if (keyIndex == -1) return url;

        int endIndex = url.indexOf("&", keyIndex);
        if (endIndex == -1) endIndex = url.length();

        String beforeKey = url.substring(0, keyIndex + 4);
        String afterKey = url.substring(endIndex);

        return beforeKey + "****" + afterKey;
    }

    /**
     * Parse JSON response từ Gemini API
     */
    private static List<Question> parseGeminiResponse(String jsonResponse, String subject, String difficulty) {
        List<Question> list = new ArrayList<>();
        Gson gson = new Gson();

        try {
            System.out.println("🔍 [AI] Bắt đầu parse response...");

            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);

            if (!root.has("candidates")) {
                System.err.println("❌ [AI] Response không có 'candidates' field");
                System.err.println("📄 [AI] Raw response: " + jsonResponse);
                return list;
            }

            JsonArray candidates = root.getAsJsonArray("candidates");

            if (candidates == null || candidates.size() == 0) {
                System.err.println("❌ [AI] Candidates array rỗng");
                return list;
            }

            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();

            if (!firstCandidate.has("content")) {
                System.err.println("❌ [AI] Candidate không có 'content' field");
                return list;
            }

            JsonObject content = firstCandidate.getAsJsonObject("content");

            if (!content.has("parts")) {
                System.err.println("❌ [AI] Content không có 'parts' field");
                return list;
            }

            JsonArray parts = content.getAsJsonArray("parts");

            if (parts == null || parts.size() == 0) {
                System.err.println("❌ [AI] Parts array rỗng");
                return list;
            }

            JsonObject firstPart = parts.get(0).getAsJsonObject();

            if (!firstPart.has("text")) {
                System.err.println("❌ [AI] Part không có 'text' field");
                return list;
            }

            String rawText = firstPart.get("text").getAsString();

            System.out.println("📝 [AI] Raw response text (first 500 chars):");
            System.out.println(rawText.substring(0, Math.min(500, rawText.length())));

            String cleanedText = cleanJsonString(rawText);

            JsonArray questionArray;
            try {
                questionArray = gson.fromJson(cleanedText, JsonArray.class);
            } catch (Exception e) {
                System.err.println("❌ [AI] Lỗi parse JSON array: " + e.getMessage());

                int startIdx = cleanedText.indexOf("[");
                int endIdx = cleanedText.lastIndexOf("]");

                if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                    String extracted = cleanedText.substring(startIdx, endIdx + 1);
                    questionArray = gson.fromJson(extracted, JsonArray.class);
                } else {
                    throw e;
                }
            }

            if (questionArray == null || questionArray.size() == 0) {
                System.err.println("⚠️ [AI] Question array rỗng sau khi parse");
                return list;
            }

            System.out.println("✅ [AI] Tìm thấy " + questionArray.size() + " câu hỏi trong response");

            for (int i = 0; i < questionArray.size(); i++) {
                try {
                    JsonObject qJson = questionArray.get(i).getAsJsonObject();
                    Question q = parseQuestionFromJson(qJson, subject, difficulty);

                    if (q != null && q.isValid()) {
                        list.add(q);
                        System.out.println("✅ [AI] Câu hỏi " + (i + 1) + " hợp lệ");
                    } else {
                        System.err.println("⚠️ [AI] Câu hỏi " + (i + 1) + " không hợp lệ");
                    }
                } catch (Exception e) {
                    System.err.println("❌ [AI] Lỗi parse câu hỏi " + (i + 1) + ": " + e.getMessage());
                }
            }

            System.out.println("🎉 [AI] Hoàn thành parse: " + list.size() + "/" + questionArray.size() + " câu hợp lệ");

        } catch (Exception e) {
            System.err.println("❌ [AI] Lỗi nghiêm trọng khi parse JSON: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    /**
     * Làm sạch chuỗi JSON từ response
     */
    private static String cleanJsonString(String rawText) {
        rawText = rawText.replace("```json", "").replace("```", "").trim();
        rawText = rawText.trim();

        if (!rawText.startsWith("[")) {
            int start = rawText.indexOf("[");
            if (start != -1) {
                rawText = rawText.substring(start);
            }
        }

        if (!rawText.endsWith("]")) {
            int end = rawText.lastIndexOf("]");
            if (end != -1) {
                rawText = rawText.substring(0, end + 1);
            }
        }

        return rawText;
    }

    /**
     * Parse một Question object từ JsonObject
     */
    private static Question parseQuestionFromJson(JsonObject json, String subject, String difficulty) {
        Question q = new Question();

        q.setSubject(subject);
        q.setDifficulty(difficulty);
        q.setPoints(10);
        q.setTimeLimit(30);
        q.setCreatedBy(1);
        q.setActive(true);

        try {
            if (json.has("questionText")) {
                q.setQuestionText(json.get("questionText").getAsString().trim());
            } else if (json.has("question_text")) {
                q.setQuestionText(json.get("question_text").getAsString().trim());
            } else if (json.has("content")) {
                q.setQuestionText(json.get("content").getAsString().trim());
            } else if (json.has("question")) {
                q.setQuestionText(json.get("question").getAsString().trim());
            }

            if (json.has("optionA")) {
                q.setOptionA(json.get("optionA").getAsString().trim());
            } else if (json.has("option_a")) {
                q.setOptionA(json.get("option_a").getAsString().trim());
            } else if (json.has("a")) {
                q.setOptionA(json.get("a").getAsString().trim());
            }

            if (json.has("optionB")) {
                q.setOptionB(json.get("optionB").getAsString().trim());
            } else if (json.has("option_b")) {
                q.setOptionB(json.get("option_b").getAsString().trim());
            } else if (json.has("b")) {
                q.setOptionB(json.get("b").getAsString().trim());
            }

            if (json.has("optionC")) {
                q.setOptionC(json.get("optionC").getAsString().trim());
            } else if (json.has("option_c")) {
                q.setOptionC(json.get("option_c").getAsString().trim());
            } else if (json.has("c")) {
                q.setOptionC(json.get("c").getAsString().trim());
            }

            if (json.has("optionD")) {
                q.setOptionD(json.get("optionD").getAsString().trim());
            } else if (json.has("option_d")) {
                q.setOptionD(json.get("option_d").getAsString().trim());
            } else if (json.has("d")) {
                q.setOptionD(json.get("d").getAsString().trim());
            }

            String correctAnswer = null;
            if (json.has("correctAnswer")) {
                correctAnswer = json.get("correctAnswer").getAsString().trim().toUpperCase();
            } else if (json.has("correct_answer")) {
                correctAnswer = json.get("correct_answer").getAsString().trim().toUpperCase();
            } else if (json.has("answer")) {
                correctAnswer = json.get("answer").getAsString().trim().toUpperCase();
            }

            if (correctAnswer != null && correctAnswer.matches("[ABCD]")) {
                q.setCorrectAnswer(correctAnswer);
            }

        } catch (Exception e) {
            System.err.println("❌ [AI] Lỗi khi parse question field: " + e.getMessage());
        }

        return q;
    }

    /**
     * Kiểm tra kết nối API
     */
    public static boolean testConnection() {
        try {
            String testPrompt = "Chỉ trả về chuỗi: OK";
            String jsonBody = buildRequestBody(testPrompt);
            HttpResponse<String> response = sendRequest(jsonBody);
            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("❌ [AI] Test connection failed: " + e.getMessage());
            return false;
        }
    }
}