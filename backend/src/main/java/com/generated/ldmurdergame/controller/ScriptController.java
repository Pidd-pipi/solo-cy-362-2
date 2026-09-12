package com.generated.ldmurdergame.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.generated.ldmurdergame.dto.ScriptRequest;
import com.generated.ldmurdergame.dto.ScriptStatusRequest;
import com.generated.ldmurdergame.model.Script;
import com.generated.ldmurdergame.service.ScriptService;

@RestController
@RequestMapping({"/scripts", "/api/scripts"})
public class ScriptController {
  private final ScriptService scriptService;

  public ScriptController(ScriptService scriptService) {
    this.scriptService = scriptService;
  }

  /** 剧本列表与搜索：可按名称（模糊）、类型、难度过滤；默认只返回启用中的剧本。 */
  @GetMapping
  public List<Script> search(@RequestParam(required = false) String name,
      @RequestParam(required = false) String genre,
      @RequestParam(required = false) String difficulty,
      @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
    return scriptService.search(name, genre, difficulty, includeInactive);
  }

  @GetMapping("/{id}")
  public Script get(@PathVariable Long id) {
    return scriptService.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Script create(@Valid @RequestBody ScriptRequest request) {
    return scriptService.create(request);
  }

  @PutMapping("/{id}")
  public Script update(@PathVariable Long id, @Valid @RequestBody ScriptRequest request) {
    return scriptService.update(id, request);
  }

  /** 停用/启用剧本：停用后不可再发布新场次，历史场次保留。 */
  @PatchMapping("/{id}/active")
  public Script setActive(@PathVariable Long id, @Valid @RequestBody ScriptStatusRequest request) {
    return scriptService.setActive(id, request.getActive());
  }
}
