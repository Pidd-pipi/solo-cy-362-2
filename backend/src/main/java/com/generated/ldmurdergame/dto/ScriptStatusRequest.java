package com.generated.ldmurdergame.dto;

import jakarta.validation.constraints.NotNull;

public class ScriptStatusRequest {
  @NotNull(message = "状态不能为空")
  private Boolean active;

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }
}
