package com.studyplatform.repository;

import com.studyplatform.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {
    List<UserActivity> findByUserIdAndDateBetweenOrderByDateDesc(Long userId, LocalDate from, LocalDate to);

    @Query("SELECT DISTINCT a.date FROM UserActivity a WHERE a.user.id = :userId ORDER BY a.date DESC")
    List<LocalDate> findDistinctDatesByUserId(Long userId);

    @Query("SELECT a.subject.name, SUM(a.durationMinutes) FROM UserActivity a " +
           "WHERE a.user.id = :userId GROUP BY a.subject.name")
    List<Object[]> findStudyTimeBySubject(Long userId);
}
