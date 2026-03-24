package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Subscription entity — manages user subscription lifecycle.
 * Integrates with Razorpay for payment processing.
 */
@Entity
@Table(name = "subscriptions", indexes = {
    @Index(name = "idx_sub_user", columnList = "user_id"),
    @Index(name = "idx_sub_status", columnList = "status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PlanType planType = PlanType.FREE;

    private String razorpaySubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE &&
               (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }

    public boolean isPro() {
        return planType == PlanType.PRO && isActive();
    }

    public enum PlanType {
        FREE, PRO
    }

    public enum SubscriptionStatus {
        ACTIVE, CANCELLED, EXPIRED, PAYMENT_PENDING
    }
}
