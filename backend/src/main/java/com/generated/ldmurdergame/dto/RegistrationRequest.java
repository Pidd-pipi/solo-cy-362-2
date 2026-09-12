package com.generated.ldmurdergame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationRequest {
  @NotBlank(message = "玩家昵称不能为空")
  @Size(max = 80, message = "玩家昵称最长 80 个字符")
  private String playerName;

  @Size(max = 120, message = "联系方式最长 120 个字符")
  private String contact;

  public String getPlayerName() {
    return playerName;
  }

  public void setPlayerName(String playerName) {
    this.playerName = playerName;
  }

  public String getContact() {
    return contact;
  }

  public void setContact(String contact) {
    this.contact = contact;
  }
}
