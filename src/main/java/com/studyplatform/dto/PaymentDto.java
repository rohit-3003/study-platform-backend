package com.studyplatform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class PaymentDto {

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class CreateOrderResponse {
        private String orderId;
        private int amount;
        private String currency;
        private String razorpayKeyId;
        private String subscriptionPlan;
    }

    @Data
    public static class VerifyPaymentRequest {
        @NotBlank
        private String razorpayPaymentId;
        @NotBlank
        private String razorpayOrderId;
        @NotBlank
        private String razorpaySignature;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class SubscriptionResponse {
        private Long id;
        private String planType;
        private String status;
        private String startsAt;
        private String expiresAt;
        private boolean isActive;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class PlanInfo {
        private String name;
        private String description;
        private int price;
        private String currency;
        private int maxTestsPerMonth;
        private int maxStudyPlans;
        private java.util.List<String> features;
    }
}
