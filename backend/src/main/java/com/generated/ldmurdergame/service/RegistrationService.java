package com.generated.ldmurdergame.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.generated.ldmurdergame.dto.RegistrationRequest;
import com.generated.ldmurdergame.exception.ApiException;
import com.generated.ldmurdergame.mapper.RegistrationMapper;
import com.generated.ldmurdergame.mapper.SessionMapper;
import com.generated.ldmurdergame.model.GameSession;
import com.generated.ldmurdergame.model.SessionRegistration;
import com.generated.ldmurdergame.model.SessionView;

@Service
public class RegistrationService {
  private final RegistrationMapper registrationMapper;
  private final SessionMapper sessionMapper;
  private final SessionService sessionService;

  public RegistrationService(RegistrationMapper registrationMapper, SessionMapper sessionMapper,
      SessionService sessionService) {
    this.registrationMapper = registrationMapper;
    this.sessionMapper = sessionMapper;
    this.sessionService = sessionService;
  }

  /**
   * 报名：在同一事务内先锁定场次行，再依次校验场次存在、重复报名、剩余名额。
   * 返回的场次视图带最新剩余名额，前端无需再次刷新。
   */
  @Transactional
  public SessionView register(Long sessionId, RegistrationRequest request) {
    String playerName = request.getPlayerName().trim();
    if (playerName.isEmpty()) {
      throw new ApiException("玩家昵称不能为空");
    }

    GameSession session = sessionMapper.findByIdForUpdate(sessionId);
    if (session == null) {
      throw new ApiException("场次不存在或已被删除，无法报名");
    }
    if (registrationMapper.findBySessionAndPlayer(sessionId, playerName) != null) {
      throw new ApiException("玩家「" + playerName + "」已报名该场次，不能重复报名");
    }
    int registered = registrationMapper.countBySession(sessionId);
    if (registered >= session.getCapacity()) {
      throw new ApiException("该场次已满员（" + session.getCapacity() + "/" + session.getCapacity()
          + "），无法报名，请关注取消后释放的空位");
    }

    SessionRegistration registration = new SessionRegistration();
    registration.setSessionId(sessionId);
    registration.setPlayerName(playerName);
    registration.setContact(request.getContact() == null ? null : request.getContact().trim());
    registrationMapper.insert(registration);
    return sessionService.getView(sessionId);
  }

  /** 取消报名：删除后空位立即释放，返回最新场次视图。 */
  @Transactional
  public SessionView cancel(Long sessionId, String playerName) {
    if (playerName == null || playerName.trim().isEmpty()) {
      throw new ApiException("玩家昵称不能为空");
    }
    String trimmedName = playerName.trim();

    // 同样锁定场次行，避免与并发报名互相覆盖
    GameSession session = sessionMapper.findByIdForUpdate(sessionId);
    if (session == null) {
      throw new ApiException("场次不存在或已被删除，无法取消报名");
    }
    int deleted = registrationMapper.deleteBySessionAndPlayer(sessionId, trimmedName);
    if (deleted == 0) {
      throw new ApiException("玩家「" + trimmedName + "」没有该场次的报名记录，无需取消");
    }
    return sessionService.getView(sessionId);
  }
}
