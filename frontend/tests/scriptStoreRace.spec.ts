import { afterEach, describe, expect, it, vi } from "vitest";
import type { Script } from "../src/types";

/**
 * 共享剧本 store 刷新竞态的固定时序测试。
 *
 * 假后端把每个 GET 请求在“发起时”做快照，并由测试手动控制何时成功/失败返回，
 * 因此可以精确复现：首次加载与保存交错、同一时刻并发刷新、旧响应晚到、旧请求失败/新请求恢复。
 * 非 GET（新增/编辑/停用/启用）立即生效。
 */
interface PendingGet {
  /** 用成功快照结束该请求 */
  resolve: () => void;
  /** 用 HTTP 500 + message 结束该请求（client 原样抛出服务端原因） */
  fail: (message: string) => void;
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
      ({
        ok: status < 300,
        status,
        json: async () => clone(payload),
      }) as Response;

    if (method === "GET") {
      getTotal += 1;
      // 发起即快照：先发请求即使晚返回也携带旧数据，用于复现“旧响应晚到”
      const payload = url.includes("includeInactive=true")
        ? clone(db)
        : clone(db.filter((s) => s.active));
      let resolveFn!: () => void;
      let failFn!: (message: string) => void;
      const promise = new Promise<Response>((resolve) => {
        resolveFn = () => resolve(res(200, payload));
        failFn = (message: string) => resolve(res(500, { message }));
      });
      pending.push({ resolve: resolveFn, fail: failFn });
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

  return { fetchImpl, db, pending, getCount: () => getTotal };
}

/** 轮询等待条件成立（覆盖 fetch→json→store 落库的若干微任务）。 */
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

const newScript = () => ({
  name: "保存后的新剧本",
  genre: "机制本",
  difficulty: "简单",
  durationMinutes: 180,
  minPlayers: 4,
  maxPlayers: 8,
});

afterEach(() => {
  vi.restoreAllMocks();
  delete (globalThis as Partial<typeof globalThis>).fetch;
});

describe("共享剧本 store：固定刷新竞态场景", () => {
  it("场景1 首次加载与保存交错：在途旧请求不被复用，最终候选为保存后的最新状态", async () => {
    const server = makeDeferredServer(seed);
    const { store, api } = await freshStore(server.fetchImpl);

    // 首次加载发出但不返回（模拟慢网络）
    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "【场景1】首次 GET 未发起");
    expect(server.getCount(), "【场景1】预期：此时仅 1 个在途请求").toBe(1);

    // 保存（新增）完成后立刻刷新；旧请求仍在途
    await api.createScript(newScript());
    const afterSave = store.refresh();
    await waitUntil(
      () => server.pending.length === 2,
      "【场景1】预期：保存后必须发起第 2 个 GET，而非复用保存前的旧请求",
    );
    expect(server.getCount()).toBe(2);

    // 旧请求先返回（携带保存前快照）：必须被丢弃，候选不能被旧数据回填
    server.pending[0].resolve();
    await Promise.resolve();
    await Promise.resolve();
    expect(store.scripts.value, "【场景1】预期：旧响应被丢弃，在新请求返回前候选保持为空，而不是旧数据")
      .toEqual([]);

    // 新请求返回保存后的最新状态
    server.pending[1].resolve();
    await Promise.all([afterSave, initialLoad]);
    const names = store.activeScripts.value.map((s) => s.name);
    expect(names, "【场景1】预期：最终候选包含刚保存的剧本，共 3 个")
      .toContain("保存后的新剧本");
    expect(store.activeScripts.value).toHaveLength(3);
  });

  it("场景2 同一时刻并发刷新：多个调用方的刷新合并为一次请求", async () => {
    const server = makeDeferredServer(seed);
    const { store } = await freshStore(server.fetchImpl);

    // 先完成首次加载
    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "【场景2】首次 GET 未发起");
    server.pending[0].resolve();
    await initialLoad;

    // 同一时刻（同一同步执行点，无 await 间隔）两个调用方同时刷新
    const concurrent = [store.refresh(), store.refresh(), store.refresh()];
    await waitUntil(
      () => server.getCount() === 2,
      "【场景2】预期：并发刷新合并后应只发起 1 个新 GET",
    );
    expect(server.getCount(), "【场景2】预期：总请求数 = 首次1 + 合并1 = 2").toBe(2);
    server.pending[1].resolve();
    await Promise.all(concurrent);

    // 再多等几拍，确认不会冒出额外请求
    await Promise.resolve();
    await Promise.resolve();
    expect(server.getCount(), "【场景2】预期：合并后无多余请求").toBe(2);
  });

  it("场景3 旧响应晚到：后发的新请求先返回、先发的旧请求后返回，旧结果不得覆盖新结果", async () => {
    const server = makeDeferredServer(seed);
    const { store, api } = await freshStore(server.fetchImpl);

    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "【场景3】首次 GET 未发起");
    server.pending[0].resolve();
    await initialLoad;

    // 请求 A（旧快照：只有 2 个剧本）
    const refreshA = store.refresh();
    await waitUntil(() => server.pending.length === 2, "【场景3】请求 A 未发起");
    await Promise.resolve();

    // 保存新增后请求 B（新快照：含新剧本）
    await api.createScript(newScript());
    const refreshB = store.refresh();
    await waitUntil(() => server.pending.length === 3, "【场景3】请求 B 未发起");

    // B（新）先返回
    server.pending[2].resolve();
    await refreshB;
    expect(store.activeScripts.value, "【场景3】预期：B 返回后候选含新剧本，共 3 个")
      .toHaveLength(3);
    expect(store.activeScripts.value.map((s) => s.name)).toContain("保存后的新剧本");

    // A（旧）晚返回：必须被丢弃，不能把候选覆盖回 2 个
    server.pending[1].resolve();
    await refreshA.catch(() => undefined);
    await Promise.resolve();
    expect(store.activeScripts.value, "【场景3】预期：旧响应 A 晚到后候选仍为 3 个且保留新剧本（未被旧数据覆盖）")
      .toHaveLength(3);
    expect(store.activeScripts.value.map((s) => s.name)).toContain("保存后的新剧本");
  });

  it("场景4 旧请求失败、新请求恢复：旧请求失败被吞掉，等待者由成功的新请求兑现", async () => {
    const server = makeDeferredServer(seed);
    const { store, api } = await freshStore(server.fetchImpl);

    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "【场景4】首次 GET 未发起");
    server.pending[0].resolve();
    await initialLoad;

    // 请求 A 发出；随后保存并发出更新的请求 B
    const refreshA = store.refresh();
    await waitUntil(() => server.pending.length === 2, "【场景4】请求 A 未发起");
    await api.createScript(newScript());
    const refreshB = store.refresh();
    await waitUntil(() => server.pending.length === 3, "【场景4】请求 B 未发起");

    // 旧请求 A 失败（网关抖动）；它已过期，失败必须被吞掉
    server.pending[1].fail("网关超时");
    await Promise.resolve();
    // 新请求 B 成功：A、B 两个等待者都应被 B 兑现（而不是被 A 的失败拒绝）
    server.pending[2].resolve();
    await expect(Promise.all([refreshA, refreshB]), "【场景4】预期：两个等待者都被新请求兑现").resolves.toBeDefined();
    expect(store.activeScripts.value.map((s) => s.name), "【场景4】预期：最终候选为 B 的最新数据，含新剧本")
      .toContain("保存后的新剧本");
  });

  it("场景5 失败恢复：请求失败不清空已有数据，下一次刷新可恢复（含首次加载失败重试）", async () => {
    const server = makeDeferredServer(seed);
    const { store } = await freshStore(server.fetchImpl);

    // 5a. 已有数据后刷新失败：保留旧数据，下一次刷新恢复
    const initialLoad = store.load();
    await waitUntil(() => server.pending.length === 1, "【场景5】首次 GET 未发起");
    server.pending[0].resolve();
    await initialLoad;
    expect(store.activeScripts.value, "【场景5】前置：候选 2 个").toHaveLength(2);

    const failed = store.refresh();
    await waitUntil(() => server.pending.length === 2, "【场景5】失败用 GET 未发起");
    server.pending[1].fail("服务暂时不可用，请稍后重试");
    await expect(failed, "【场景5】预期：失败请求向外抛出服务端原因").rejects.toThrow("服务暂时不可用");
    expect(store.activeScripts.value, "【场景5】预期：失败不清空已有数据，候选仍为 2 个")
      .toHaveLength(2);

    const recovered = store.refresh();
    await waitUntil(() => server.pending.length === 3, "【场景5】恢复用 GET 未发起");
    server.pending[2].resolve();
    await recovered;
    expect(store.activeScripts.value, "【场景5】预期：下一次刷新成功恢复").toHaveLength(2);

    // 5b. 首次加载就失败：数据为空，再次加载可恢复
    vi.resetModules();
    const server2 = makeDeferredServer(seed);
    globalThis.fetch = server2.fetchImpl;
    const mod2 = await import("../src/state/scripts");
    const store2 = mod2.useScriptStore();

    const first = store2.load();
    await waitUntil(() => server2.pending.length === 1, "【场景5b】首次 GET 未发起");
    server2.pending[0].fail("首次加载失败");
    await expect(first, "【场景5b】预期：首次失败抛出原因").rejects.toThrow("首次加载失败");
    expect(store2.scripts.value, "【场景5b】预期：首次失败后数据为空").toEqual([]);

    const retry = store2.load();
    await waitUntil(() => server2.pending.length === 2, "【场景5b】重试 GET 未发起");
    server2.pending[1].resolve();
    await retry;
    expect(store2.activeScripts.value, "【场景5b】预期：重试成功恢复，候选 2 个").toHaveLength(2);
  });
});
