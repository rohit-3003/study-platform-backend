package com.studyplatform.service;

import com.studyplatform.dto.PaymentDto;
import com.studyplatform.entity.*;
import com.studyplatform.exception.GlobalExceptionHandler.*;
import com.studyplatform.repository.*;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class SubscriptionService {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    @Value("${razorpay.key-id}") private String razorpayKeyId;
    @Value("${razorpay.key-secret}") private String razorpayKeySecret;
    @Value("${subscription.pro.price}") private int proPrice;
    @Value("${subscription.pro.currency}") private String proCurrency;

    private final SubscriptionRepository subRepo;
    private final PaymentRepository paymentRepo;
    private final AuthService authService;

    public SubscriptionService(SubscriptionRepository subRepo, PaymentRepository paymentRepo,
                               AuthService authService) {
        this.subRepo = subRepo;
        this.paymentRepo = paymentRepo;
        this.authService = authService;
    }

    public List<PaymentDto.PlanInfo> getPlans() {
        return List.of(
            PaymentDto.PlanInfo.builder().name("Free").description("Basic access")
                .price(0).currency("INR").maxTestsPerMonth(5).maxStudyPlans(1)
                .features(List.of("5 mock tests/month", "1 study plan", "Basic analytics")).build(),
            PaymentDto.PlanInfo.builder().name("Pro").description("Full access")
                .price(proPrice).currency(proCurrency).maxTestsPerMonth(-1).maxStudyPlans(-1)
                .features(List.of("Unlimited mock tests", "Unlimited study plans",
                    "Advanced analytics", "Rank predictor", "AI explanations", "Priority support")).build()
        );
    }

    @Transactional
    public PaymentDto.CreateOrderResponse createOrder() {
        User user = authService.getCurrentUser();
        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject options = new JSONObject();
            options.put("amount", proPrice * 100); // paise
            options.put("currency", proCurrency);
            options.put("receipt", "rcpt_" + user.getId() + "_" + System.currentTimeMillis());
            Order order = client.orders.create(options);
            Payment payment = Payment.builder().user(user)
                    .razorpayOrderId(order.get("id")).amount(proPrice)
                    .currency(proCurrency).status(Payment.PaymentStatus.PENDING).build();
            paymentRepo.save(payment);
            return PaymentDto.CreateOrderResponse.builder()
                    .orderId(order.get("id")).amount(proPrice * 100)
                    .currency(proCurrency).razorpayKeyId(razorpayKeyId)
                    .subscriptionPlan("PRO").build();
        } catch (Exception e) {
            logger.error("Razorpay order creation failed: {}", e.getMessage());
            throw new PaymentException("Failed to create payment order. Please try again.");
        }
    }

    @Transactional
    public PaymentDto.SubscriptionResponse verifyPayment(PaymentDto.VerifyPaymentRequest req) {
        User user = authService.getCurrentUser();
        // Verify signature
        String payload = req.getRazorpayOrderId() + "|" + req.getRazorpayPaymentId();
        if (!verifySignature(payload, req.getRazorpaySignature())) {
            throw new PaymentException("Payment verification failed. Invalid signature.");
        }
        Payment payment = paymentRepo.findByRazorpayOrderId(req.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        payment.setRazorpayPaymentId(req.getRazorpayPaymentId());
        payment.setRazorpaySignature(req.getRazorpaySignature());
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        paymentRepo.save(payment);
        // Activate Pro subscription
        Subscription sub = Subscription.builder().user(user)
                .planType(Subscription.PlanType.PRO)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .startsAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30)).build();
        sub = subRepo.save(sub);
        payment.setSubscription(sub);
        paymentRepo.save(payment);
        logger.info("Pro subscription activated for: {}", user.getEmail());
        return PaymentDto.SubscriptionResponse.builder()
                .id(sub.getId()).planType("PRO").status("ACTIVE")
                .startsAt(sub.getStartsAt().toString())
                .expiresAt(sub.getExpiresAt().toString()).isActive(true).build();
    }

    public PaymentDto.SubscriptionResponse getCurrentSubscription() {
        User user = authService.getCurrentUser();
        Subscription sub = subRepo.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .orElse(null);
        if (sub == null) {
            return PaymentDto.SubscriptionResponse.builder()
                    .planType("FREE").status("ACTIVE").isActive(true).build();
        }
        return PaymentDto.SubscriptionResponse.builder()
                .id(sub.getId()).planType(sub.getPlanType().name())
                .status(sub.getStatus().name())
                .startsAt(sub.getStartsAt() != null ? sub.getStartsAt().toString() : null)
                .expiresAt(sub.getExpiresAt() != null ? sub.getExpiresAt().toString() : null)
                .isActive(sub.isActive()).build();
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(), "HmacSHA256"));
            String generated = HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
            return generated.equals(signature);
        } catch (Exception e) {
            logger.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
