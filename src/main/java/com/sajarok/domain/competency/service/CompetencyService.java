package com.sajarok.domain.competency.service;

import com.sajarok.domain.competency.entity.Competency;
import com.sajarok.domain.competency.entity.Competency.Scope;
import com.sajarok.domain.competency.repository.CompetencyRepository;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
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
}
