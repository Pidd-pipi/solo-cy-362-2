package com.generated.ldmurdergame.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.generated.ldmurdergame.dto.SessionRequest;
import com.generated.ldmurdergame.exception.ApiException;
import com.generated.ldmurdergame.mapper.ScriptMapper;
import com.generated.ldmurdergame.mapper.SessionMapper;
import com.generated.ldmurdergame.model.GameSession;
import com.generated.ldmurdergame.model.Script;
import com.generated.ldmurdergame.model.SessionRegistration;
import com.generated.ldmurdergame.model.SessionView;

@Service
public class SessionService {
  private final SessionMapper sessionMapper;
  private final ScriptMapper scriptMapper;

  public SessionService(SessionMapper sessionMapper, ScriptMapper scriptMapper) {
    this.sessionMapper = sessionMapper;
    this.scriptMapper = scriptMapper;
  }

  public List<SessionView> listViews() {
    List<SessionView> views = sessionMapper.findAllViews();
    if (views.isEmpty()) {
      return views;
    }
    List<Long> sessionIds = views.stream().map(SessionView::getId).collect(Collectors.toList());
    Map<Long, List<SessionRegistration>> registrationsBySession = sessionMapper.findBySessionIds(sessionIds)
        .stream()
        .collect(Collectors.groupingBy(SessionRegistration::getSessionId));
    views.forEach(view -> fillView(view, registrationsBySession.getOrDefault(view.getId(), List.of())));
    return views;
  }

  public SessionView getView(Long id) {
    SessionView view = sessionMapper.findViewById(id);
    if (view == null) {
      throw new ApiException("场次不存在或已被删除");
    }
    fillView(view, sessionMapper.findBySessionIds(List.of(id)));
    return view;
  }

  @Transactional
  public SessionView create(SessionRequest request) {
    Script script = scriptMapper.findById(request.getScriptId());
    if (script == null) {
      throw new ApiException("所选剧本不存在，无法发布场次");
    }
    if (!Boolean.TRUE.equals(script.getActive())) {
      throw new ApiException("剧本「" + script.getName() + "」已停用，不能发布新场次");
    }
    LocalDateTime startTime = parseStartTime(request.getStartTime());
    if (startTime.isBefore(LocalDateTime.now())) {
      throw new ApiException("开场时间不能早于当前时间");
    }
    if (request.getCapacity() < script.getMinPlayers()) {
      throw new ApiException("人数上限不能低于剧本要求的最少人数（" + script.getMinPlayers() + " 人）");
    }
    if (request.getCapacity() > script.getMaxPlayers()) {
      throw new ApiException("人数上限不能超过剧本支持的最大人数（" + script.getMaxPlayers() + " 人）");
    }

    GameSession session = new GameSession();
    session.setScriptId(script.getId());
    session.setStartTime(startTime);
    session.setHostName(request.getHostName().trim());
    session.setCapacity(request.getCapacity());
    sessionMapper.insert(session);
    return getView(session.getId());
  }

  private void fillView(SessionView view, List<SessionRegistration> registrations) {
    int registered = registrations.size();
    int remaining = Math.max(0, view.getCapacity() - registered);
    view.setRegisteredCount(registered);
    view.setRemainingSlots(remaining);
    view.setFull(remaining == 0);
    view.setRegistrations(registrations);
  }

  static LocalDateTime parseStartTime(String value) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw new ApiException("开场时间不能为空");
    }
    // 兼容 datetime-local 提交的 "yyyy-MM-ddTHH:mm" 及完整格式
    String normalized = trimmed.replace('T', ' ');
    List<DateTimeFormatter> formatters = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    for (DateTimeFormatter formatter : formatters) {
      try {
        return LocalDateTime.parse(normalized, formatter);
      } catch (DateTimeParseException ignored) {
        // 尝试下一种格式
      }
    }
    throw new ApiException("开场时间格式不正确，请选择有效的日期时间");
  }
}
