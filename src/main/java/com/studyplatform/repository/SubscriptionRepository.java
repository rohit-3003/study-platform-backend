package com.studyplatform.repository;

import com.studyplatform.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUserIdAndStatus(Long userId, Subscription.SubscriptionStatus status);
    Optional<Subscription> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
