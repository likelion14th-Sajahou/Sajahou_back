package com.sajarok.domain.week.service;

import com.sajarok.domain.user.entity.Part;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.global.time.KstTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeekService {

  private final JdbcTemplate jdbcTemplate;

  public CurrentWeek findCurrentWeek(Part part) {
    LocalDate today = LocalDate.now(KstTime.OFFSET);

    return jdbcTemplate
        .query(
            """

                    SELECT w.id, w.week_number, w.phase,
                               s.session_title, s.session_deadline, s.assignment_deadline
                        FROM week w
                        LEFT JOIN week_part_schedule s
                          ON s.week_id = w.id AND s.part = ?
                        WHERE w.start_date <= ?
                          AND w.start_date > ?
                        ORDER BY w.start_date DESC
                        LIMIT 1
                        """,
            (rs, rowNum) ->
                new CurrentWeek(
                    rs.getLong("id"),
                    rs.getInt("week_number"),
                    rs.getString("phase"),
                    rs.getString("session_title"),
                    KstTime.toOffset(rs.getObject("session_deadline", LocalDateTime.class)),
                    KstTime.toOffset(rs.getObject("assignment_deadline", LocalDateTime.class))),
            part.name(),
            today,
            today.minusDays(7))
        .stream()
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "현재 주차 일정이 없어요."));
  }

  public record CurrentWeek(
      Long weekId,
      int weekNumber,
      String phase,
      String sessionTitle,
      OffsetDateTime sessionDeadline,
      OffsetDateTime assignmentDeadline) {}

  public Map<Long, Integer> findWeekNumbers(Collection<Long> weekIds) {
    if (weekIds.isEmpty()) {
      return Map.of();
    }

    return new NamedParameterJdbcTemplate(jdbcTemplate)
        .query(
            "SELECT id, week_number FROM week WHERE id IN (:ids)",
            Map.of("ids", weekIds),
            rs -> {
              Map<Long, Integer> numbers = new HashMap<>();
              while (rs.next()) {
                numbers.put(rs.getLong("id"), rs.getInt("week_number"));
              }
              return numbers;
            });
  }

  public Map<Long, String> findPresentationPhases(Collection<Long> weekIds) {
    if (weekIds.isEmpty()) {
      return Map.of();
    }

    return new NamedParameterJdbcTemplate(jdbcTemplate)
        .query(
            """
                    SELECT id, phase
                    FROM week
                    WHERE id IN (:ids)
                      AND is_presentation_week = TRUE
                    """,
            Map.of("ids", weekIds),
            rs -> {
              Map<Long, String> phases = new HashMap<>();
              while (rs.next()) {
                phases.put(rs.getLong("id"), rs.getString("phase"));
              }
              return phases;
            });
  }

  public List<FootprintWeek> findFootprintWeeks(Part part) {
    return jdbcTemplate.query(
        """
                SELECT w.id, w.week_number, w.phase, w.is_presentation_week, s.session_title
                FROM week w
                LEFT JOIN week_part_schedule s
                  ON s.week_id = w.id AND s.part = ?
                ORDER BY w.week_number
                """,
        (rs, rowNum) ->
            new FootprintWeek(
                rs.getLong("id"),
                rs.getInt("week_number"),
                rs.getString("phase"),
                rs.getBoolean("is_presentation_week"),
                rs.getString("session_title")),
        part.name());
  }

  public record FootprintWeek(
      Long weekId, int weekNumber, String phase, boolean isPresentationWeek, String sessionTitle) {}
}
