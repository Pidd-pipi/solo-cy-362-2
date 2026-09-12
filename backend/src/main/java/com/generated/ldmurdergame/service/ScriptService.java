package com.generated.ldmurdergame.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.generated.ldmurdergame.dto.ScriptRequest;
import com.generated.ldmurdergame.exception.ApiException;
import com.generated.ldmurdergame.mapper.ScriptMapper;
import com.generated.ldmurdergame.model.Script;

@Service
public class ScriptService {
  private final ScriptMapper scriptMapper;

  public ScriptService(ScriptMapper scriptMapper) {
    this.scriptMapper = scriptMapper;
  }

  public List<Script> search(String name, String genre, String difficulty, boolean includeInactive) {
    return scriptMapper.search(trimToNull(name), trimToNull(genre), trimToNull(difficulty), includeInactive);
  }

  public Script get(Long id) {
    Script script = scriptMapper.findById(id);
    if (script == null) {
      throw new ApiException("剧本不存在或已被删除");
    }
    return script;
  }

  @Transactional
  public Script create(ScriptRequest request) {
    validatePlayerRange(request);
    Script script = new Script();
    applyRequest(script, request);
    scriptMapper.insert(script);
    return scriptMapper.findById(script.getId());
  }

  @Transactional
  public Script update(Long id, ScriptRequest request) {
    Script script = get(id);
    validatePlayerRange(request);
    applyRequest(script, request);
    scriptMapper.update(script);
    return scriptMapper.findById(id);
  }

  @Transactional
  public Script setActive(Long id, boolean active) {
    get(id);
    scriptMapper.updateActive(id, active);
    return scriptMapper.findById(id);
  }

  private void applyRequest(Script script, ScriptRequest request) {
    script.setName(request.getName().trim());
    script.setGenre(request.getGenre().trim());
    script.setDifficulty(request.getDifficulty().trim());
    script.setDurationMinutes(request.getDurationMinutes());
    script.setMinPlayers(request.getMinPlayers());
    script.setMaxPlayers(request.getMaxPlayers());
    script.setDmRequirement(trimToNull(request.getDmRequirement()));
    script.setDescription(trimToNull(request.getDescription()));
  }

  private void validatePlayerRange(ScriptRequest request) {
    if (request.getMinPlayers() > request.getMaxPlayers()) {
      throw new ApiException("最少人数不能大于人数上限");
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
