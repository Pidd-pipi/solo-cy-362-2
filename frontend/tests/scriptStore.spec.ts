import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Script } from "../src/types";

function seedScripts(): Script[] {
  return [
    { id: 1, name: "年轮", genre: "推理本", difficulty: "困难", durationMinutes: 300, minPlayers: 4, maxPlayers: 6, active: true },
    { id: 2, name: "拆迁", genre: "欢乐本", difficulty: "简单", durationMinutes: 240, minPlayers: 5, maxPlayers: 8, active: true },
    { id: 3, name: "古木吟", genre: "情感本", difficulty: "中等", durationMinutes: 280, minPlayers: 3, maxPlayers: 6, active: true },
    { id: 4, name: "病娇男孩的精分日记", genre: "恐怖本", difficulty: "中等", durationMinutes: 300, minPlayers: 4, maxPlayers: 7, active: true },
  ];
}

/**
 * 内存假后端：模拟真实 HTTP 语义——每次响应都经 JSON 往返返回全新对象引用，
 * 否则 Vue 会因引用未变而正确地跳过更新，掩盖问题。
 */
function makeServer(initial: Script[]) {
  let seq = 100;
  const db = structuredClone(initial);
  const clone = (value: unknown) => JSON.parse(JSON.stringify(value));
  const handler = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input);
    const method = init.method || "GET";
    const body = init.body ? JSON.parse(String(init.body)) : {};
    const res = (status: number, payload: unknown) => ({
      ok: status < 300,
      status,
      json: async () => clone(payload),
    });

    if (method === "GET") {
      const list = url.includes("includeInactive=true") ? db : db.filter((s) => s.active);
      return res(200, list);
    }
    if (method === "POST") {
      const created = { id: ++seq, ...body, active: true } as Script;
      db.push(created);
      return res(201, created);
    }
    let match = url.match(/\/scripts\/(\d+)$/);
    if (match && method === "PUT") {
      const target = db.find((s) => s.id === Number(match![1]))!;
      Object.assign(target, body);
      return res(200, target);
    }
    match = url.match(/\/scripts\/(\d+)\/active$/);
    if (match && method === "PATCH") {
      const target = db.find((s) => s.id === Number(match![1]))!;
      target.active = body.active;
      return res(200, target);
    }
    return res(404, { message: "未匹配的请求: " + method + " " + url });
  });
  return { handler, db };
}

/** 每个用例重置模块注册表，store 单例状态从零开始，保证可连续重复运行。 */
async function freshModules(fetchImpl: typeof fetch) {
  vi.resetModules();
  globalThis.fetch = fetchImpl;
  const { useScriptStore } = await import("../src/state/scripts");
  const api = await import("../src/api/client");
  return { useScriptStore, api };
}

afterEach(() => {
  vi.restoreAllMocks();
  delete (globalThis as Partial<typeof globalThis>).fetch;
});

describe("剧本共享 store：场次候选与剧本管理页实时同步", () => {
  beforeEach(() => {
    vi.useRealTimers();
  });

  it("初始加载后，候选为全部启用剧本", async () => {
    const server = makeServer(seedScripts());
    const { useScriptStore } = await freshModules(server.handler);
    const store = useScriptStore();

    await store.load();
    expect(store.activeScripts.value).toHaveLength(4);
    expect(server.handler).toHaveBeenCalledTimes(1);
  });

  it("新增剧本后立即出现在候选，无需刷新（期望：4 → 5，含新剧本）", async () => {
    const server = makeServer(seedScripts());
    const { useScriptStore, api } = await freshModules(server.handler);
    const store = useScriptStore();
    await store.load();

    const created = await api.createScript({
      name: "新剧本Z", genre: "机制本", difficulty: "简单",
      durationMinutes: 180, minPlayers: 4, maxPlayers: 8,
    });
    await store.refresh();

    expect(store.activeScripts.value).toHaveLength(5);
    expect(store.activeScripts.value.map((s) => s.name)).toContain("新剧本Z");
    expect(created.active).toBe(true);
  });

  it("编辑剧本后候选中名称与类型即时更新，旧值消失（期望：仍5个，显示新名/阵营本）", async () => {
    const server = makeServer(seedScripts());
    const { useScriptStore, api } = await freshModules(server.handler);
    const store = useScriptStore();
    await store.load();
    const created = await api.createScript({
      name: "新剧本Z", genre: "机制本", difficulty: "简单",
      durationMinutes: 180, minPlayers: 4, maxPlayers: 8,
    });
    await store.refresh();

    await api.updateScript(created.id, {
      name: "新剧本Z-改名", genre: "阵营本", difficulty: "中等",
      durationMinutes: 200, minPlayers: 4, maxPlayers: 9,
    });
    await store.refresh();

    const names = store.activeScripts.value.map((s) => s.name);
    expect(names).not.toContain("新剧本Z");
    expect(names).toContain("新剧本Z-改名");
    const edited = store.activeScripts.value.find((s) => s.id === created.id)!;
    expect(edited.genre).toBe("阵营本");
    expect(edited.difficulty).toBe("中等");
    expect(store.activeScripts.value).toHaveLength(5);
  });

  it("停用剧本立即离开候选但数据保留；停用种子剧本后候选继续减少（期望：5→4→3）", async () => {
    const server = makeServer(seedScripts());
    const { useScriptStore, api } = await freshModules(server.handler);
    const store = useScriptStore();
    await store.load();
    const created = await api.createScript({
      name: "新剧本Z", genre: "机制本", difficulty: "简单",
      durationMinutes: 180, minPlayers: 4, maxPlayers: 8,
    });
    await store.refresh();

    await api.setScriptActive(created.id, false);
    await store.refresh();
    expect(store.activeScripts.value).toHaveLength(4);
    expect(store.activeScripts.value.map((s) => s.name)).not.toContain("新剧本Z");
    // 停用不是删除：全量列表仍保留且 active=false
    const retained = store.scripts.value.find((s) => s.id === created.id)!;
    expect(retained.active).toBe(false);

    await api.setScriptActive(1, false);
    await store.refresh();
    expect(store.activeScripts.value).toHaveLength(3);
    expect(store.activeScripts.value.map((s) => s.name)).not.toContain("年轮");
  });

  it("重新启用后候选恢复（期望：回到5，两个剧本重新出现）", async () => {
    const server = makeServer(seedScripts());
    const { useScriptStore, api } = await freshModules(server.handler);
    const store = useScriptStore();
    await store.load();
    const created = await api.createScript({
      name: "新剧本Z", genre: "机制本", difficulty: "简单",
      durationMinutes: 180, minPlayers: 4, maxPlayers: 8,
    });
    await store.refresh();
    await api.setScriptActive(created.id, false);
    await api.setScriptActive(1, false);
    await store.refresh();
    expect(store.activeScripts.value).toHaveLength(3);

    await api.setScriptActive(created.id, true);
    await api.setScriptActive(1, true);
    await store.refresh();

    expect(store.activeScripts.value).toHaveLength(5);
    const names = store.activeScripts.value.map((s) => s.name);
    expect(names).toContain("新剧本Z");
    expect(names).toContain("年轮");
  });

  it("已加载后 load() 走缓存不重复请求，refresh() 强制重新拉取", async () => {
    const server = makeServer(seedScripts());
    const { useScriptStore } = await freshModules(server.handler);
    const store = useScriptStore();
    await store.load();
    await store.load();
    expect(server.handler).toHaveBeenCalledTimes(1);

    await store.refresh();
    expect(server.handler).toHaveBeenCalledTimes(2);
  });
});
