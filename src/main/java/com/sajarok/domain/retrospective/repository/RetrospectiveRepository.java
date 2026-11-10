package com.sajarok.domain.retrospective.repository;

import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetrospectiveRepository extends JpaRepository<Retrospective, Long> {

  Optional<Retrospective> findByIdAndUserId(Long id, Long userId);

  Optional<Retrospective> findByUserIdAndWeekIdAndType(
      Long userId, Long weekId, RetrospectiveType type);

  List<Retrospective> findAllByUserId(Long userId);
}
