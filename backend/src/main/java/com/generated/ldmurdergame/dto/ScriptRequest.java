package com.generated.ldmurdergame.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ScriptRequest {
  @NotBlank(message = "剧本名称不能为空")
  @Size(max = 120, message = "剧本名称最长 120 个字符")
  private String name;

  @NotBlank(message = "剧本类型不能为空")
  @Size(max = 40, message = "剧本类型最长 40 个字符")
  private String genre;

  @NotBlank(message = "难度不能为空")
  @Size(max = 20, message = "难度最长 20 个字符")
  private String difficulty;

  @NotNull(message = "时长（分钟）不能为空")
  @Min(value = 1, message = "时长必须大于 0")
  private Integer durationMinutes;

  @NotNull(message = "最少人数不能为空")
  @Min(value = 1, message = "最少人数至少为 1")
  private Integer minPlayers;

  @NotNull(message = "人数上限不能为空")
  @Min(value = 1, message = "人数上限至少为 1")
  @Max(value = 30, message = "人数上限不能超过 30")
  private Integer maxPlayers;

  @Size(max = 120, message = "主持人要求最长 120 个字符")
  private String dmRequirement;

  @Size(max = 500, message = "简介最长 500 个字符")
  private String description;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(Integer durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  public Integer getMinPlayers() {
    return minPlayers;
  }

  public void setMinPlayers(Integer minPlayers) {
    this.minPlayers = minPlayers;
  }

  public Integer getMaxPlayers() {
    return maxPlayers;
  }

  public void setMaxPlayers(Integer maxPlayers) {
    this.maxPlayers = maxPlayers;
  }

  public String getDmRequirement() {
    return dmRequirement;
  }

  public void setDmRequirement(String dmRequirement) {
    this.dmRequirement = dmRequirement;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
