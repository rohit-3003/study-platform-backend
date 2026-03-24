package com.studyplatform.controller;

import com.studyplatform.dto.ApiResponse;
import com.studyplatform.dto.PaymentDto;
import com.studyplatform.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<PaymentDto.PlanInfo>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getPlans()));
    }

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentDto.CreateOrderResponse>> createOrder() {
        return ResponseEntity.ok(ApiResponse.success("Order created",
                subscriptionService.createOrder()));
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<ApiResponse<PaymentDto.SubscriptionResponse>> verifyPayment(
            @Valid @RequestBody PaymentDto.VerifyPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment verified",
                subscriptionService.verifyPayment(request)));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<PaymentDto.SubscriptionResponse>> getCurrentSub() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getCurrentSubscription()));
    }
}
