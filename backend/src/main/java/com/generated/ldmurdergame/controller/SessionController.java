package com.generated.ldmurdergame.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.generated.ldmurdergame.dto.SessionRequest;
import com.generated.ldmurdergame.model.SessionView;
import com.generated.ldmurdergame.service.SessionService;

@RestController
@RequestMapping({"/sessions", "/api/sessions"})
public class SessionController {
  private final SessionService sessionService;

  public SessionController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  /** 场次列表：含剧本名、已报名人数、剩余名额、是否满员及报名名单。 */
  @GetMapping
  public List<SessionView> list() {
    return sessionService.listViews();
  }

  @GetMapping("/{id}")
  public SessionView get(@PathVariable Long id) {
    return sessionService.getView(id);
  }

  /** 发布场次：选择剧本、开场时间、主持人和人数上限。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SessionView create(@Valid @RequestBody SessionRequest request) {
    return sessionService.create(request);
  }
}
