package com.generated.ldmurdergame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发报名：全程走完整 HTTP 请求链（真实随机端口 + TestRestTemplate），
 * 多线程同时调用 POST /api/sessions/{id}/registrations，
 * 验证“多人抢最后一个名额时只有一人成功、绝不超卖”。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RegistrationConcurrencyHttpTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  private String api;

  private static final String START_TIME = "2099-09-01 19:00:00";

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /** 发起一次真实 HTTP 调用并把状态码与 JSON 一并返回（含 4xx，不抛异常）。 */
  private ResponseEntity<JsonNode> postJson(String path, Object body) {
    HttpEntity<String> entity = new HttpEntity<>(writeJson(body), jsonHeaders());
    return restTemplate.postForEntity(api + path, entity, JsonNode.class);
  }

  private JsonNode getJson(String path) {
    return restTemplate.getForObject(api + path, JsonNode.class);
  }

  private static String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private long createScript(String name, int min, int max) {
    ResponseEntity<JsonNode> response = postJson("/scripts", Map.of(
        "name", name, "genre", "机制本", "difficulty", "中等",
        "durationMinutes", 240, "minPlayers", min, "maxPlayers", max));
    assertThat(response.getStatusCode().value())
        .as("前置：创建剧本应成功（实际 %s）", response.getBody())
        .isEqualTo(201);
    return response.getBody().path("id").asLong();
  }

  private long createSession(long scriptId, int capacity) {
    ResponseEntity<JsonNode> response = postJson("/sessions", Map.of(
        "scriptId", scriptId, "startTime", START_TIME, "hostName", "DM-阿杰", "capacity", capacity));
    assertThat(response.getStatusCode().value())
        .as("前置：发布场次应成功（实际 %s）", response.getBody())
        .isEqualTo(201);
    return response.getBody().path("id").asLong();
  }

  @BeforeEach
  void setUp() {
    api = "http://localhost:" + port + "/api";
  }

  @Test
  @DisplayName("完整HTTP链：8人同时抢最后1个名额，恰好1人成功(200)，其余7人被拒(400已满员)，最终不超卖")
  void raceForLastSlot_overHttp_exactlyOneWins() throws Exception {
    int capacity = 4;
    long scriptId = createScript("抢位本", 4, 6);
    long sessionId = createSession(scriptId, capacity);

    // 前置：通过真实 HTTP 接口报满 3 人，仅剩 1 个名额
    for (int i = 0; i < capacity - 1; i++) {
      ResponseEntity<JsonNode> response =
          postJson("/sessions/" + sessionId + "/registrations", Map.of("playerName", "占位玩家" + i));
      assertThat(response.getStatusCode().value())
          .as("前置占位报名应成功（实际 %s）", response.getBody()).isEqualTo(200);
    }
    assertThat(getJson("/sessions/" + sessionId).path("remainingSlots").asInt())
        .as("前置：应恰好剩 1 个名额").isEqualTo(1);

    int racers = 8;
    ExecutorService pool = Executors.newFixedThreadPool(racers);
    CountDownLatch ready = new CountDownLatch(racers);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    ConcurrentLinkedQueue<String> failureReasons = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> failureStatus = new ConcurrentLinkedQueue<>();
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < racers; i++) {
      final String player = "抢位玩家" + i;
      futures.add(pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
          // 关键：每个线程都发起一次独立的完整 HTTP 请求
          ResponseEntity<JsonNode> response =
              postJson("/sessions/" + sessionId + "/registrations", Map.of("playerName", player));
          if (response.getStatusCode().is2xxSuccessful()) {
            success.incrementAndGet();
          } else {
            failureStatus.add(response.getStatusCode().value());
            failureReasons.add(response.getBody().path("message").asText());
          }
        } catch (Exception exception) {
          failureReasons.add("HTTP异常:" + exception.getMessage());
        }
      }));
    }

    assertThat(ready.await(10, TimeUnit.SECONDS))
        .as("场景『并发抢位』：所有线程应在 10 秒内就绪").isTrue();
    start.countDown();
    for (Future<?> future : futures) {
      future.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();

    JsonNode finalView = getJson("/sessions/" + sessionId);
    assertThat(success.get())
        .as("场景『8人抢1位』预期：成功人数=1；实际成功=%d，HTTP状态=%s，失败原因=%s",
            success.get(), failureStatus, failureReasons)
        .isEqualTo(1);
    assertThat(finalView.path("registeredCount").asInt())
        .as("场景『并发抢位』预期：最终已报名=%d（不超卖）；实际=%d", capacity,
            finalView.path("registeredCount").asInt())
        .isEqualTo(capacity);
    assertThat(finalView.path("remainingSlots").asInt())
        .as("场景『并发抢位』预期：剩余名额=0").isZero();
    assertThat(finalView.path("full").asBoolean())
        .as("场景『并发抢位』预期：场次满员=true").isTrue();
    assertThat(failureStatus)
        .as("场景『并发抢位』预期：失败请求数=%d 且状态码均为400", racers - 1)
        .hasSize(racers - 1).allSatisfy(status -> assertThat(status).isEqualTo(400));
    assertThat(failureReasons)
        .as("场景『并发抢位』预期：失败原因均提示满员")
        .allSatisfy(reason -> assertThat(reason).contains("已满员"));
  }

  @Test
  @DisplayName("完整HTTP链：同一玩家双击并发提交，只成功1次，另一次400重复报名")
  void duplicatePlayerDoubleClick_overHttp_onlyOneSucceeds() throws Exception {
    long scriptId = createScript("双击本", 4, 6);
    long sessionId = createSession(scriptId, 4);

    int threads = 2;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<String> reasons = new ConcurrentLinkedQueue<>();
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < threads; i++) {
      futures.add(pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
          ResponseEntity<JsonNode> response =
              postJson("/sessions/" + sessionId + "/registrations", Map.of("playerName", "双击玩家"));
          statuses.add(response.getStatusCode().value());
          if (!response.getStatusCode().is2xxSuccessful()) {
            reasons.add(response.getBody().path("message").asText());
          }
        } catch (Exception exception) {
          reasons.add("HTTP异常:" + exception.getMessage());
        }
      }));
    }
    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    for (Future<?> future : futures) {
      future.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();

    JsonNode view = getJson("/sessions/" + sessionId);
    assertThat(statuses)
        .as("场景『同一玩家双击并发』预期：1 个 200、1 个 400；实际状态=%s，原因=%s", statuses, reasons)
        .containsExactlyInAnyOrder(200, 400);
    assertThat(view.path("registeredCount").asInt())
        .as("场景『双击并发』预期：最终只报名 1 人").isEqualTo(1);
    assertThat(reasons)
        .as("场景『双击并发』预期：失败原因提示不能重复报名")
        .hasSize(1).allSatisfy(reason -> assertThat(reason).contains("已报名该场次", "不能重复报名"));
  }

  @Test
  @DisplayName("完整HTTP链：取消释放1空位后5人并发补位，只1人成功，总数回到容量")
  void refillAfterCancel_overHttp_onlyOneWins() throws Exception {
    int capacity = 2;
    long scriptId = createScript("小车本", 2, 2);
    long sessionId = createSession(scriptId, capacity);

    assertThat(postJson("/sessions/" + sessionId + "/registrations", Map.of("playerName", "占位甲"))
        .getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(postJson("/sessions/" + sessionId + "/registrations", Map.of("playerName", "占位乙"))
        .getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(getJson("/sessions/" + sessionId).path("remainingSlots").asInt())
        .as("前置：小车应已满员").isZero();

    // 通过完整 HTTP DELETE 取消一人，释放空位。
    // 用 URI 对象传参：RestTemplate 对 String 会二次编码百分号，URI 对象只编码一次，Tomcat 正确解码中文
    java.net.URI cancelUri = org.springframework.web.util.UriComponentsBuilder
        .fromHttpUrl(api + "/sessions/" + sessionId + "/registrations")
        .queryParam("playerName", "占位甲")
        .encode()
        .build()
        .toUri();
    ResponseEntity<JsonNode> cancelled =
        restTemplate.exchange(cancelUri, org.springframework.http.HttpMethod.DELETE,
            new HttpEntity<>(jsonHeaders()), JsonNode.class);
    assertThat(cancelled.getStatusCode().value())
        .as("前置：取消报名应成功（实际 %s）", cancelled.getBody()).isEqualTo(200);
    assertThat(cancelled.getBody().path("remainingSlots").asInt())
        .as("前置：取消后应剩 1 个空位").isEqualTo(1);

    int racers = 5;
    ExecutorService pool = Executors.newFixedThreadPool(racers);
    CountDownLatch ready = new CountDownLatch(racers);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    ConcurrentLinkedQueue<String> reasons = new ConcurrentLinkedQueue<>();
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < racers; i++) {
      final String player = "补位玩家" + i;
      futures.add(pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
          ResponseEntity<JsonNode> response =
              postJson("/sessions/" + sessionId + "/registrations", Map.of("playerName", player));
          if (response.getStatusCode().is2xxSuccessful()) {
            success.incrementAndGet();
          } else {
            reasons.add(response.getBody().path("message").asText());
          }
        } catch (Exception exception) {
          reasons.add("HTTP异常:" + exception.getMessage());
        }
      }));
    }
    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    for (Future<?> future : futures) {
      future.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();

    JsonNode view = getJson("/sessions/" + sessionId);
    assertThat(success.get())
        .as("场景『取消后并发补位』预期：恰好1人补位成功；实际=%d，原因=%s", success.get(), reasons)
        .isEqualTo(1);
    assertThat(view.path("registeredCount").asInt())
        .as("场景『补位』预期：总人数回到容量 %d 且不超卖", capacity).isEqualTo(capacity);
    assertThat(view.path("remainingSlots").asInt()).isZero();
    assertThat(view.path("full").asBoolean()).isTrue();
    assertThat(reasons).hasSize(racers - 1).allSatisfy(reason -> assertThat(reason).contains("已满员"));
  }
}
