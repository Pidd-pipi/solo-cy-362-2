package com.generated.ldmurdergame.model;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 场次视图：聚合剧本信息、报名人数、剩余名额与报名名单。
 * 每次报名/取消后由后端实时计算返回，保证剩余名额立即更新。
 */
public class SessionView {
  private Long id;
  private Long scriptId;
  private String scriptName;
  private String genre;
  private String difficulty;
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime startTime;
  private String hostName;
  private Integer capacity;
  private Integer registeredCount;
  private Integer remainingSlots;
  private Boolean full;
  private List<SessionRegistration> registrations;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getScriptId() {
    return scriptId;
  }

  public void setScriptId(Long scriptId) {
    this.scriptId = scriptId;
  }

  public String getScriptName() {
    return scriptName;
  }

  public void setScriptName(String scriptName) {
    this.scriptName = scriptName;
  }

  public String getGenre() {
    return genre;
  }

  public void setGenre(String genre) {
    this.genre = genre;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String difficulty) {
    this.difficulty = difficulty;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalDateTime startTime) {
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

  public Integer getRegisteredCount() {
    return registeredCount;
  }

  public void setRegisteredCount(Integer registeredCount) {
    this.registeredCount = registeredCount;
  }

  public Integer getRemainingSlots() {
    return remainingSlots;
  }

  public void setRemainingSlots(Integer remainingSlots) {
    this.remainingSlots = remainingSlots;
  }

  public Boolean getFull() {
    return full;
  }

  public void setFull(Boolean full) {
    this.full = full;
  }

  public List<SessionRegistration> getRegistrations() {
    return registrations;
  }

  public void setRegistrations(List<SessionRegistration> registrations) {
    this.registrations = registrations;
  }
}
