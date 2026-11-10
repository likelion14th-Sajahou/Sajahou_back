package com.sajarok.domain.retrospective.service;

import com.sajarok.domain.retrospective.dto.HomeRetroSummary;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.dto.WeekRetroStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetrospectiveService {

  public HomeRetroSummary homeSummary(Long userId, Long currentWeekId) {
    return new HomeRetroSummary(null, RetrospectiveState.NOT_STARTED, List.of(), true); // TODO fake
  }

  public List<WeekRetroStatus> footprints(Long userId) {
    return List.of();
  }
}
