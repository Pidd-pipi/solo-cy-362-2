package com.generated.ldmurdergame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import com.generated.ldmurdergame.support.ApiHelper;
import com.generated.ldmurdergame.support.ApiHelper.Response;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 剧本新增、编辑、停用、启用后，场次候选（GET /api/scripts 默认仅启用）无需刷新即同步，
 * 并覆盖名称/类型/难度查找与“停用剧本不可发布场次，启用后恢复”。
 */
@Sql(scripts = "/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ScriptLifecycleIntegrationTest extends AbstractIntegrationTest {

  private long scriptId;

  @BeforeEach
  void setUp() throws Exception {
    scriptId = ApiHelper.createScript(mockMvc, "年轮", "推理本", "困难", 300, 4, 6);
  }

  @Test
  @DisplayName("新增剧本后，立即出现在场次候选列表（无需刷新）")
  void create_appearsInCandidatesImmediately() throws Exception {
    Response before = ApiHelper.getJson(mockMvc, "/api/scripts");
    assertThat(before.json()).hasSize(1);

    Response created = ApiHelper.post(mockMvc, "/api/scripts", java.util.Map.of(
        "name", "拆迁", "genre", "欢乐本", "difficulty", "简单",
        "durationMinutes", 240, "minPlayers", 5, "maxPlayers", 8));
    assertThat(created.status()).isEqualTo(201);

    // 紧接着的请求即为前端发布场次时读取的候选，应已包含新剧本
    Response candidates = ApiHelper.getJson(mockMvc, "/api/scripts");
    assertThat(candidates.status()).isEqualTo(200);
    assertThat(candidates.json()).hasSize(2);
    assertThat(candidates.raw()).contains("拆迁");
  }

  @Test
  @DisplayName("编辑剧本后，候选中名称/类型/难度即时更新，旧值消失")
  void edit_reflectedInCandidatesImmediately() throws Exception {
    Response updated = ApiHelper.put(mockMvc, "/api/scripts/" + scriptId, java.util.Map.of(
        "name", "年轮2", "genre", "机制本", "difficulty", "中等",
        "durationMinutes", 320, "minPlayers", 4, "maxPlayers", 7));
    assertThat(updated.status()).isEqualTo(200);

    Response candidates = ApiHelper.getJson(mockMvc, "/api/scripts");
    assertThat(candidates.raw()).contains("年轮2").contains("机制本").contains("中等");
    assertThat(candidates.raw()).doesNotContain("\"name\":\"年轮\"");
  }

  @Test
  @DisplayName("停用剧本后立即离开候选；includeInactive 仍可查到；重新启用后回到候选")
  void disable_thenEnable_candidateSync() throws Exception {
    Response disabled = ApiHelper.patch(mockMvc, "/api/scripts/" + scriptId + "/active",
        java.util.Map.of("active", false));
    assertThat(disabled.status()).isEqualTo(200);
    assertThat(disabled.json().path("active").asBoolean()).isFalse();

    Response activeOnly = ApiHelper.getJson(mockMvc, "/api/scripts");
    assertThat(activeOnly.json()).isEmpty();

    Response withInactive = ApiHelper.getJson(mockMvc, "/api/scripts?includeInactive=true");
    assertThat(withInactive.json()).hasSize(1);
    assertThat(withInactive.json().get(0).path("active").asBoolean()).isFalse();

    Response enabled = ApiHelper.patch(mockMvc, "/api/scripts/" + scriptId + "/active",
        java.util.Map.of("active", true));
    assertThat(enabled.json().path("active").asBoolean()).isTrue();

    Response candidates = ApiHelper.getJson(mockMvc, "/api/scripts");
    assertThat(candidates.json()).hasSize(1);
    assertThat(candidates.json().get(0).path("name").asText()).isEqualTo("年轮");
  }

  @Test
  @DisplayName("停用剧本不能发布新场次；重新启用后可发布，且已有场次/数据语义不受影响")
  void disabledScript_cannotPublishSession_untilReenabled() throws Exception {
    ApiHelper.patch(mockMvc, "/api/scripts/" + scriptId + "/active", java.util.Map.of("active", false));

    Response blocked = ApiHelper.post(mockMvc, "/api/sessions", java.util.Map.of(
        "scriptId", scriptId, "startTime", "2099-01-01 10:00:00", "hostName", "DM-X", "capacity", 5));
    assertThat(blocked.status()).isEqualTo(400);
    assertThat(blocked.message()).contains("已停用", "不能发布新场次");

    ApiHelper.patch(mockMvc, "/api/scripts/" + scriptId + "/active", java.util.Map.of("active", true));
    Response published = ApiHelper.post(mockMvc, "/api/sessions", java.util.Map.of(
        "scriptId", scriptId, "startTime", "2099-01-01 10:00:00", "hostName", "DM-X", "capacity", 5));
    assertThat(published.status()).isEqualTo(201);
    assertThat(published.json().path("remainingSlots").asInt()).isEqualTo(5);
  }

  @Test
  @DisplayName("按名称、类型、难度查找剧本")
  void search_byNameGenreDifficulty() throws Exception {
    ApiHelper.post(mockMvc, "/api/scripts", java.util.Map.of(
        "name", "拆迁", "genre", "欢乐本", "difficulty", "简单",
        "durationMinutes", 240, "minPlayers", 5, "maxPlayers", 8));

    assertThat(ApiHelper.getJson(mockMvc, "/api/scripts?name=年").json()).hasSize(1);
    assertThat(ApiHelper.getJson(mockMvc, "/api/scripts?genre=欢乐本").json()).hasSize(1);
    assertThat(ApiHelper.getJson(mockMvc, "/api/scripts?difficulty=困难").json()).hasSize(1);
    assertThat(ApiHelper.getJson(mockMvc, "/api/scripts?name=不存在的本子").json()).isEmpty();
  }

  @Test
  @DisplayName("参数非法时返回 400 与具体中文原因")
  void invalidPayload_returnsReason() throws Exception {
    Response response = ApiHelper.post(mockMvc, "/api/scripts", java.util.Map.of(
        "name", "非法本", "genre", "推理本", "difficulty", "简单",
        "durationMinutes", 180, "minPlayers", 9, "maxPlayers", 6));
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.message()).contains("最少人数不能大于人数上限");
  }
}
