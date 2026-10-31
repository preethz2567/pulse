package dev.preethi.pulse.repository;

import dev.preethi.pulse.entity.StatusPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StatusPageRepository extends JpaRepository<StatusPage, Long> {

    @Query("SELECT DISTINCT sp FROM StatusPage sp LEFT JOIN FETCH sp.monitors WHERE sp.user.id = :userId ORDER BY sp.createdAt DESC")
    List<StatusPage> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT sp FROM StatusPage sp LEFT JOIN FETCH sp.monitors WHERE sp.id = :id AND sp.user.id = :userId")
    Optional<StatusPage> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    Optional<StatusPage> findBySlug(String slug);

    @Query("SELECT sp FROM StatusPage sp LEFT JOIN FETCH sp.monitors WHERE sp.slug = :slug")
    Optional<StatusPage> findBySlugWithMonitors(@Param("slug") String slug);

    boolean existsBySlug(String slug);
}
