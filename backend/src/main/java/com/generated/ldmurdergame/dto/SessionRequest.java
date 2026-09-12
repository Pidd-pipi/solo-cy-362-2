package com.generated.ldmurdergame.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SessionRequest {
  @NotNull(message = "请选择剧本")
  private Long scriptId;

  @NotBlank(message = "开场时间不能为空")
  private String startTime;

  @NotBlank(message = "主持人不能为空")
  @Size(max = 80, message = "主持人名称最长 80 个字符")
  private String hostName;

  @NotNull(message = "人数上限不能为空")
  @Min(value = 1, message = "人数上限至少为 1")
  @Max(value = 30, message = "人数上限不能超过 30")
  private Integer capacity;

  public Long getScriptId() {
    return scriptId;
  }

  public void setScriptId(Long scriptId) {
    this.scriptId = scriptId;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public String getHostName() {
    return hostName;
  }

  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  public Integer getCapacity() {
    return capacity;
  }

  public void setCapacity(Integer capacity) {
    this.capacity = capacity;
  }
}
