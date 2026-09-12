import { afterEach, describe, expect, it, vi } from "vitest";
import type { Script } from "../src/types";

/**
 * 竞态测试专用假后端：GET 请求在发起时快照数据并可由测试手动控制何时返回/失败，
 * 从而精确复现“首次加载与保存交错、连续刷新、乱序返回、请求失败”等时序。
 * 非 GET 请求（保存动作）立即生效并返回。
 */
interface PendingGet {
  payload: unknown;
  resolve: () => void;
  /** 以 HTTP 错误响应结束（response.ok=false），client 会原样抛出 message */
  fail: (message: string) => void;
  reject: (error: unknown) => void;
  promise: Promise<Response>;
}

function makeDeferredServer(initial: Script[]) {
  let seq = 100;
  const db: Script[] = structuredClone(initial);
  const pending: PendingGet[] = [];
  let getTotal = 0;

  const clone = (value: unknown) => JSON.parse(JSON.stringify(value));

  const fetchImpl = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input);
    const method = init.method || "GET";
    const body = init.body ? JSON.parse(String(init.body)) : {};
    const res = (status: number, payload: unknown): Response =>
      ({ ok: status < 300, status, json: async () => clone(payload) }) as Response;

    if (method === "GET") {
      getTotal += 1;
      // 发起时快照：让先发出的请求即便晚返回，也携带旧数据，用于复现乱序覆盖
      const payload = url.includes("includeInactive=true") ? clone(db) : clone(db.filter((s) => s.active));
      let resolveFn!: () => void;
      let failFn!: (message: string) => void;
      let rejectFn!: (error: unknown) => void;
      const promise = new Promise<Response>((resolve, reject) => {
        resolveFn = () => resolve(res(200, payload));
        failFn = (message) => resolve(res(500, { message }));
        rejectFn = (error) => reject(error);
      });
      pending.push({ payload, resolve: resolveFn, fail: failFn, reject: rejectFn, promise });
      return promise;
    }
    if (method === "POST") {
      const created = { id: ++seq, ...body, active: true } as Script;
      db.push(created);
      return res(201, created);
    }
    let match = url.match(/\/scripts\/(\d+)$/);
    if (match && method === "PUT") {
      Object.assign(db.find((s) => s.id === Number(match![1]))!, body);
      return res(200, {});
    }
    match = url.match(/\/scripts\/(\d+)\/active$/);
    if (match && method === "PATCH") {
      db.find((s) => s.id === Number(match![1]))!.active = body.active;
      return res(200, {});
    }
    return res(404, { message: "未匹配 " + method + " " + url });
  });

  return {
    fetchImpl,
    db,
    pending,
    getCount: () => getTotal,
  };
}

async function waitUntil(predicate: () => boolean, message: string) {
  for (let i = 0; i < 100; i += 1) {
    if (predicate()) return;
    await Promise.resolve();
  }
  throw new Error(message);
}

async function freshStore(fetchImpl: typeof fetch) {
  vi.resetModules();
  globalThis.fetch = fetchImpl;
  const { useScriptStore } = await import("../src/state/scripts");
  const api = await import("../src/api/client");
  return { store: useScriptStore(), api };
}

const seed: Script[] = [
  { id: 1, name: "年轮", genre: "推理本", difficulty: "困难", durationMinutes: 300, minPlayers: 4, maxPlayers: 6, active: true },
  { id: 2, name: "拆迁", genre: "欢乐本", difficulty: "简单", durationMinutes: 240, minPlayers: 5, maxPlayers: 8, active: true },
];

afterEach(() => {
  vi.restoreAllMocks();
  delete (globalThis as Partial<typeof globalThis>).fetch;
});

describe("共享剧本 store：刷新竞态", () => {
  it("首次加载在途时保存触发刷新：不复用旧请求，最终候选为保存后最新状态", async () => {
    const server = makeDeferredServer(seed);
    const { store, api } = await freshStore(server.fetchImpl);

    // 首次加载发出，但故意不返回
    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "首次 GET 未发起");
    expect(server.getCount()).toBe(1);

    // 保存（新增剧本）完成，紧接着刷新；旧请求仍在途
    await api.createScript({
      name: "保存后的新剧本", genre: "机制本", difficulty: "简单",
      durationMinutes: 180, minPlayers: 4, maxPlayers: 8,
    });
    const refreshDone = store.refresh();
    await waitUntil(() => server.pending.length === 2, "刷新未发起新 GET（可能错误复用了旧请求）");
    expect(server.getCount()).toBe(2);

    // 旧请求先返回（携带保存前的旧数据），必须被丢弃，候选仍为空而不是旧数据
    server.pending[0].resolve();
    await Promise.resolve();
    await Promise.resolve();
    expect(store.scripts.value).toEqual([]);

    // 新请求返回保存后的最新状态
    server.pending[1].resolve();
    await refreshDone;
    await initialLoad;
    const names = store.activeScripts.value.map((s) => s.name);
    expect(names).toContain("保存后的新剧本");
    expect(store.activeScripts.value).toHaveLength(3);
  });

  it("同一微任务内并发刷新合并为一次请求", async () => {
    const server = makeDeferredServer(seed);
    const { store } = await freshStore(server.fetchImpl);

    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "首次 GET 未发起");
    server.pending[0].resolve();
    await initialLoad;

    // 两个调用方在同一 tick 内同时刷新（无 await 间隔）
    const calls = [store.refresh(), store.refresh()];
    await waitUntil(() => server.getCount() === 2, "合并后的 GET 未发起");
    expect(server.getCount()).toBe(2);
    server.pending[1].resolve();
    await Promise.all(calls);

    // 再等若干微任务，确认不会冒出第三次请求
    await Promise.resolve();
    await Promise.resolve();
    expect(server.getCount()).toBe(2);
  });

  it("乱序返回：后发的请求先回来、先发的旧请求后回来，旧结果不得覆盖新结果", async () => {
    const server = makeDeferredServer(seed);
    const { store, api } = await freshStore(server.fetchImpl);

    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "首次 GET 未发起");
    server.pending[0].resolve();
    await initialLoad;

    // 第一次刷新 -> 请求 A（快照：只有2个剧本）
    const refreshA = store.refresh();
    await waitUntil(() => server.pending.length === 2, "请求 A 未发起");
    await Promise.resolve();
    // 保存新增剧本后，第二次刷新 -> 请求 B（快照：含新剧本）
    await api.createScript({
      name: "仅在B中存在的剧本", genre: "阵营本", difficulty: "中等",
      durationMinutes: 200, minPlayers: 4, maxPlayers: 9,
    });
    const refreshB = store.refresh();
    await waitUntil(() => server.pending.length === 3, "请求 B 未发起");

    // B（新）先返回
    server.pending[2].resolve();
    await refreshB;
    expect(store.activeScripts.value.map((s) => s.name)).toContain("仅在B中存在的剧本");
    expect(store.activeScripts.value).toHaveLength(3);

    // A（旧）晚返回，必须被丢弃
    server.pending[1].resolve();
    await refreshA.catch(() => undefined);
    await Promise.resolve();
    const names = store.activeScripts.value.map((s) => s.name);
    expect(names).toContain("仅在B中存在的剧本");
    expect(store.activeScripts.value).toHaveLength(3);
  });

  it("请求失败后下一次刷新能恢复，且失败不清空已有数据", async () => {
    const server = makeDeferredServer(seed);
    const { store } = await freshStore(server.fetchImpl);

    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "首次 GET 未发起");
    server.pending[0].resolve();
    await initialLoad;
    expect(store.activeScripts.value).toHaveLength(2);

    // 刷新失败（HTTP 500）：调用方收到错误，已有候选保留，在途状态被清理
    const failed = store.refresh();
    await waitUntil(() => server.pending.length === 2, "失败场景的 GET 未发起");
    server.pending[1].fail("服务暂时不可用，请稍后重试");
    await expect(failed).rejects.toThrow("服务暂时不可用，请稍后重试");
    expect(store.activeScripts.value).toHaveLength(2);

    // 下一次刷新可正常恢复并拿到数据
    const recovered = store.refresh();
    await waitUntil(() => server.pending.length === 3, "恢复用 GET 未发起");
    server.pending[2].resolve();
    await recovered;
    expect(store.activeScripts.value).toHaveLength(2);
  });

  it("交错场景中旧请求失败不影响更新请求的等待者", async () => {
    const server = makeDeferredServer(seed);
    const { store, api } = await freshStore(server.fetchImpl);

    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "首次 GET 未发起");
    server.pending[0].resolve();
    await initialLoad;

    const refreshA = store.refresh();
    await waitUntil(() => server.pending.length === 2, "请求 A 未发起");
    await api.createScript({
      name: "新剧本", genre: "推理本", difficulty: "简单",
      durationMinutes: 180, minPlayers: 4, maxPlayers: 6,
    });
    const refreshB = store.refresh();
    await waitUntil(() => server.pending.length === 3, "请求 B 未发起");

    // 旧请求 A 以 HTTP 500 失败（例如网关抖动）；A 已过期，其失败必须被吞掉，
    // 等待者全部由更新的请求 B 兑现，而不是被旧失败拒绝
    server.pending[1].fail("旧请求失败");
    await Promise.resolve();
    server.pending[2].resolve();
    await expect(Promise.all([refreshA, refreshB])).resolves.toBeDefined();
    expect(store.activeScripts.value.map((s) => s.name)).toContain("新剧本");
  });

  it("首次加载失败后，再次加载可以恢复", async () => {
    const server = makeDeferredServer(seed);
    const { store } = await freshStore(server.fetchImpl);

    const first = store.load();
    await waitUntil(() => server.pending.length === 1, "首次 GET 未发起");
    server.pending[0].fail("首次加载失败");
    await expect(first).rejects.toThrow("首次加载失败");
    expect(store.scripts.value).toEqual([]);

    const retry = store.load();
    await waitUntil(() => server.pending.length === 2, "重试 GET 未发起");
    server.pending[1].resolve();
    await retry;
    expect(store.activeScripts.value).toHaveLength(2);
  });
});
