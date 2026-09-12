package com.generated.ldmurdergame;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import com.generated.ldmurdergame.support.ApiHelper;
import com.generated.ldmurdergame.support.ApiHelper.Response;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 拼车报名主流程：正常报名（名额立即更新）、重复报名被拒、满员不能报名、
 * 取消后空位释放并可再次报名。每个用例独立清表，可连续重复运行。
 */
@Sql(scripts = "/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RegistrationFlowIntegrationTest extends AbstractIntegrationTest {

  private static final String START_TIME = "2099-06-01 14:00:00";
  private long sessionId;

  @BeforeEach
  void setUp() throws Exception {
    long scriptId = ApiHelper.createScript(mockMvc, "年轮", "推理本", "困难", 300, 3, 6);
    sessionId = ApiHelper.createSession(mockMvc, scriptId, START_TIME, "DM-小鹿", 3);
  }

  private Response register(String player) throws Exception {
    return ApiHelper.post(mockMvc, "/api/sessions/" + sessionId + "/registrations",
        Map.of("playerName", player));
  }

  private Response cancel(String player) throws Exception {
    return ApiHelper.deleteWithParam(mockMvc,
        "/api/sessions/" + sessionId + "/registrations", "playerName", player);
  }

  @Test
  @DisplayName("正常报名成功，剩余名额立即更新（无需再刷新）")
  void register_updatesRemainingSlotsImmediately() throws Exception {
    Response first = register("玩家A");
    assertThat(first.status()).isEqualTo(200);
    assertThat(first.json().path("registeredCount").asInt()).isEqualTo(1);
    assertThat(first.json().path("remainingSlots").asInt()).isEqualTo(2);
    assertThat(first.json().path("full").asBoolean()).isFalse();
    assertThat(first.raw()).contains("玩家A");

    Response second = register("玩家B");
    assertThat(second.json().path("registeredCount").asInt()).isEqualTo(2);
    assertThat(second.json().path("remainingSlots").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("同一玩家重复报名被拒绝，并说明原因")
  void duplicateRegistration_isRejectedWithReason() throws Exception {
    assertThat(register("玩家A").status()).isEqualTo(200);

    Response duplicate = register("玩家A");
    assertThat(duplicate.status()).isEqualTo(400);
    assertThat(duplicate.message()).contains("已报名该场次", "不能重复报名");

    // 被拒后名额不变
    Response view = ApiHelper.getJson(mockMvc, "/api/sessions/" + sessionId);
    assertThat(view.json().path("registeredCount").asInt()).isEqualTo(1);
    assertThat(view.json().path("remainingSlots").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName("满员后不能继续报名，并明确提示满员")
  void fullSession_rejectsRegistration() throws Exception {
    assertThat(register("玩家A").status()).isEqualTo(200);
    assertThat(register("玩家B").status()).isEqualTo(200);
    assertThat(register("玩家C").status()).isEqualTo(200);

    Response view = ApiHelper.getJson(mockMvc, "/api/sessions/" + sessionId);
    assertThat(view.json().path("registeredCount").asInt()).isEqualTo(3);
    assertThat(view.json().path("remainingSlots").asInt()).isZero();
    assertThat(view.json().path("full").asBoolean()).isTrue();

    Response overflow = register("玩家D");
    assertThat(overflow.status()).isEqualTo(400);
    assertThat(overflow.message()).contains("已满员");

    // 满员状态稳定：列表接口同样标记满员
    Response list = ApiHelper.getJson(mockMvc, "/api/sessions");
    assertThat(list.json().get(0).path("full").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("取消报名后空位立即释放，其他玩家可立即重报该名额")
  void cancel_freesSlotForReregistration() throws Exception {
    register("玩家A");
    register("玩家B");
    register("玩家C");
    assertThat(ApiHelper.getJson(mockMvc, "/api/sessions/" + sessionId).json().path("remainingSlots").asInt())
        .isZero();

    Response cancelled = cancel("玩家A");
    assertThat(cancelled.status()).isEqualTo(200);
    assertThat(cancelled.json().path("registeredCount").asInt()).isEqualTo(2);
    assertThat(cancelled.json().path("remainingSlots").asInt()).isEqualTo(1);
    assertThat(cancelled.json().path("full").asBoolean()).isFalse();

    Response reRegister = register("玩家D");
    assertThat(reRegister.status()).isEqualTo(200);
    assertThat(reRegister.json().path("remainingSlots").asInt()).isZero();
    assertThat(reRegister.raw()).contains("玩家D").doesNotContain("\"playerName\":\"玩家A\"");
  }

  @Test
  @DisplayName("取消不存在的报名被拒绝并说明原因，名额不变")
  void cancelNonExistent_isRejected() throws Exception {
    register("玩家A");
    Response response = cancel("幽灵玩家");
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.message()).contains("没有该场次的报名记录");

    Response view = ApiHelper.getJson(mockMvc, "/api/sessions/" + sessionId);
    assertThat(view.json().path("registeredCount").asInt()).isEqualTo(1);
    assertThat(view.json().path("remainingSlots").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName("报名昵称为空被拒绝并说明原因")
  void blankPlayerName_isRejected() throws Exception {
    Response response = ApiHelper.post(mockMvc, "/api/sessions/" + sessionId + "/registrations",
        Map.of("playerName", "   "));
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.message()).contains("玩家昵称不能为空");
  }
}
