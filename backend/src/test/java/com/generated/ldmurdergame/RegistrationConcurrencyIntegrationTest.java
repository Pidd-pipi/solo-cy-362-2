package com.generated.ldmurdergame;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.context.jdbc.Sql;
import com.generated.ldmurdergame.dto.RegistrationRequest;
import com.generated.ldmurdergame.model.SessionView;
import com.generated.ldmurdergame.service.RegistrationService;
import com.generated.ldmurdergame.service.SessionService;
import com.generated.ldmurdergame.support.ApiHelper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发场景：多名玩家同时抢最后一个名额。
 * 通过事务内行锁（SELECT ... FOR UPDATE）+ (session_id, player_name) 唯一约束保证：
 * 恰好 1 人成功，其余全部得到“已满员”原因，最终报名数严格等于容量，绝不超卖。
 */
@Sql(scripts = "/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RegistrationConcurrencyIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private RegistrationService registrationService;

  @Autowired
  private SessionService sessionService;

  private static final String START_TIME = "2099-09-01 19:00:00";
  private long sessionId;

  @BeforeEach
  void setUp() throws Exception {
    long scriptId = ApiHelper.createScript(mockMvc, "抢位本", "机制本", "中等", 240, 4, 6);
    sessionId = ApiHelper.createSession(mockMvc, scriptId, START_TIME, "DM-阿杰", 4);
  }

  private RegistrationRequest request(String playerName) {
    RegistrationRequest request = new RegistrationRequest();
    request.setPlayerName(playerName);
    return request;
  }

  @Test
  @DisplayName("并发抢最后一个名额：恰好1人成功，其余被拒且名额不超卖")
  void raceForLastSlot_exactlyOneWins() throws Exception {
    int capacity = 4;
    // 先占位 3 人，仅剩 1 个名额
    for (int i = 0; i < capacity - 1; i++) {
      registrationService.register(sessionId, request("占位玩家" + i));
    }
    assertThat(sessionService.getView(sessionId).getRemainingSlots()).isEqualTo(1);

    int racers = 8;
    ExecutorService pool = Executors.newFixedThreadPool(racers);
    CountDownLatch ready = new CountDownLatch(racers);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    ConcurrentLinkedQueue<String> failureReasons = new ConcurrentLinkedQueue<>();
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < racers; i++) {
      final String player = "抢位玩家" + i;
      futures.add(pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
          SessionView view = registrationService.register(sessionId, request(player));
          success.incrementAndGet();
          assertThat(view.getRemainingSlots()).isZero();
        } catch (Exception exception) {
          failureReasons.add(exception.getMessage());
        }
      }));
    }

    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    for (Future<?> future : futures) {
      future.get(20, TimeUnit.SECONDS);
    }
    pool.shutdown();

    SessionView finalView = sessionService.getView(sessionId);
    assertThat(success.get())
        .as("并发抢位成功人数必须恰好为 1（实际 %d），最终报名 %d/%d，失败原因：%s",
            success.get(), finalView.getRegisteredCount(), capacity, failureReasons)
        .isEqualTo(1);
    assertThat(finalView.getRegisteredCount())
        .as("不能超卖：最终报名人数必须等于容量").isEqualTo(capacity);
    assertThat(finalView.getRemainingSlots()).isZero();
    assertThat(finalView.getFull()).isTrue();
    assertThat(failureReasons).hasSize(racers - 1);
    assertThat(failureReasons).allSatisfy(reason -> assertThat(reason).contains("已满员"));
  }

  @Test
  @DisplayName("同一玩家并发重复提交：恰好1次成功，另一次得到重复报名原因")
  void concurrentDuplicatePlayer_onlyOneSucceeds() throws Exception {
    int threads = 2;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    ConcurrentLinkedQueue<String> reasons = new ConcurrentLinkedQueue<>();
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < threads; i++) {
      futures.add(pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
          registrationService.register(sessionId, request("双击玩家"));
          success.incrementAndGet();
        } catch (Exception exception) {
          reasons.add(exception.getMessage());
        }
      }));
    }
    ready.await(10, TimeUnit.SECONDS);
    start.countDown();
    for (Future<?> future : futures) {
      future.get(20, TimeUnit.SECONDS);
    }
    pool.shutdown();

    SessionView view = sessionService.getView(sessionId);
    assertThat(success.get())
        .as("同一玩家并发提交只能成功一次（实际 %d，原因：%s）", success.get(), reasons)
        .isEqualTo(1);
    assertThat(view.getRegisteredCount()).isEqualTo(1);
    assertThat(reasons).hasSize(1);
    assertThat(reasons.peek()).contains("已报名该场次", "不能重复报名");
  }

  @Test
  @DisplayName("取消释放1个空位后多人并发补位：恰好1人补位成功，总数回到容量且不超卖")
  void concurrentRegisterAfterCancel_slotReusedOnce() throws Exception {
    int capacity = 2;
    long smallSession = ApiHelper.createSession(
        mockMvc, ApiHelper.createScript(mockMvc, "小车本", "欢乐本", "简单", 180, 2, 2),
        START_TIME, "DM-X", capacity);
    registrationService.register(smallSession, request("占位甲"));
    registrationService.register(smallSession, request("占位乙"));
    assertThat(sessionService.getView(smallSession).getRemainingSlots()).isZero();

    // 先确定性地完成取消（模拟玩家退车），再让多名玩家同时抢这个空位
    SessionView cancelled = registrationService.cancel(smallSession, "占位甲");
    assertThat(cancelled.getRegisteredCount()).isEqualTo(1);
    assertThat(cancelled.getRemainingSlots()).isEqualTo(1);

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
          registrationService.register(smallSession, request(player));
          success.incrementAndGet();
        } catch (Exception exception) {
          reasons.add(exception.getMessage());
        }
      }));
    }
    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    for (Future<?> future : futures) {
      future.get(20, TimeUnit.SECONDS);
    }
    pool.shutdown();

    SessionView view = sessionService.getView(smallSession);
    assertThat(success.get())
        .as("释放的1个空位必须恰好被1人抢到（成功 %d，原因：%s）", success.get(), reasons)
        .isEqualTo(1);
    assertThat(view.getRegisteredCount())
        .as("补位后总人数必须等于容量且不超卖").isEqualTo(capacity);
    assertThat(view.getRemainingSlots()).isZero();
    assertThat(view.getFull()).isTrue();
    assertThat(reasons).hasSize(racers - 1);
    assertThat(reasons).allSatisfy(reason -> assertThat(reason).contains("已满员"));
  }
}
