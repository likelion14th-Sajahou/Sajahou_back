package com.sajarok.domain.competency.repository;

import com.sajarok.domain.competency.entity.Competency;
import com.sajarok.domain.competency.entity.Competency.Scope;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyRepository extends JpaRepository<Competency, Long> {

  List<Competency> findAllByPartInOrderBySortOrderAscIdAsc(Collection<Scope> parts);
}
