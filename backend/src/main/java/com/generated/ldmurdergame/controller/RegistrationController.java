package com.generated.ldmurdergame.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.generated.ldmurdergame.dto.RegistrationRequest;
import com.generated.ldmurdergame.model.SessionView;
import com.generated.ldmurdergame.service.RegistrationService;

@RestController
@RequestMapping({"/sessions", "/api/sessions"})
public class RegistrationController {
  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  /** 玩家报名；成功后返回最新场次视图（剩余名额立即更新）。满员或重复报名返回 400 与原因。 */
  @PostMapping("/{id}/registrations")
  public SessionView register(@PathVariable Long id, @Valid @RequestBody RegistrationRequest request) {
    return registrationService.register(id, request);
  }

  /** 取消报名：空位立即释放，可被其他玩家再次报名。 */
  @DeleteMapping("/{id}/registrations")
  public SessionView cancel(@PathVariable Long id, @RequestParam String playerName) {
    return registrationService.cancel(id, playerName);
  }
}
