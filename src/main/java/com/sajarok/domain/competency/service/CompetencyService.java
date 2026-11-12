package com.sajarok.domain.competency.service;

import com.sajarok.domain.competency.entity.Competency;
import com.sajarok.domain.competency.entity.Competency.Scope;
import com.sajarok.domain.competency.repository.CompetencyRepository;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetencyService {

  private final UserRepository userRepository;
  private final CompetencyRepository competencyRepository;

  public List<Competency> findForUser(Long userId) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    Scope userScope = Scope.valueOf(user.getPart().name());
    return competencyRepository.findAllByPartInOrderBySortOrderAscIdAsc(
        List.of(Scope.COMMON, userScope));
  }

  public Set<Long> validate(Long userId, Collection<Long> ids, boolean required) {
    Set<Long> distinctIds = ids == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);

    if (distinctIds.size() > 3 || (required && distinctIds.isEmpty())) {
      throw new BusinessException(ErrorCode.COMPETENCY_LIMIT);
    }
    if (distinctIds.isEmpty()) {
      return distinctIds;
    }

    Set<Long> availableIds =
        findForUser(userId).stream().map(Competency::getId).collect(Collectors.toSet());

    if (!availableIds.containsAll(distinctIds)) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "선택할 수 없는 역량 태그가 있어요.");
    }

    return distinctIds;
  }

  public Map<Long, String> names(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }

    return competencyRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Competency::getId, Competency::getName));
  }
}
