import { afterEach, describe, expect, it, vi } from "vitest";
import { defineComponent, h, nextTick } from "vue";
import { mount } from "@vue/test-utils";
import type { Script } from "../src/types";

/**
 * 两个真实挂载的组件模拟两个页面：
 * - Consumer 模拟「发布场次」页，渲染场次候选（store.activeScripts）；
 * - Operator 模拟「剧本管理」页，调用真实 API 模块后 store.refresh()。
 * 全程不重新挂载 Consumer（=不刷新页面），断言其 DOM 随增/改/停/启即时变化。
 */
function seedScripts(): Script[] {
  return [
    { id: 1, name: "年轮", genre: "推理本", difficulty: "困难", durationMinutes: 300, minPlayers: 4, maxPlayers: 6, active: true },
    { id: 2, name: "拆迁", genre: "欢乐本", difficulty: "简单", durationMinutes: 240, minPlayers: 5, maxPlayers: 8, active: true },
    { id: 3, name: "古木吟", genre: "情感本", difficulty: "中等", durationMinutes: 280, minPlayers: 3, maxPlayers: 6, active: true },
    { id: 4, name: "病娇男孩的精分日记", genre: "恐怖本", difficulty: "中等", durationMinutes: 300, minPlayers: 4, maxPlayers: 7, active: true },
  ];
}

function makeServer(initial: Script[]) {
  let seq = 100;
  const db = structuredClone(initial);
  const clone = (value: unknown) => JSON.parse(JSON.stringify(value));
  const fetchImpl = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input);
    const method = init.method || "GET";
    const body = init.body ? JSON.parse(String(init.body)) : {};
    const res = (status: number, payload: unknown) => ({
      ok: status < 300,
      status,
      json: async () => clone(payload),
    });
    if (method === "GET") {
      return res(200, url.includes("includeInactive=true") ? db : db.filter((s) => s.active));
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
  return { fetchImpl, db };
}

async function setupHarness(initial: Script[]) {
  vi.resetModules();
  const server = makeServer(initial);
  globalThis.fetch = server.fetchImpl as unknown as typeof fetch;
  const { useScriptStore } = await import("../src/state/scripts");
  const api = await import("../src/api/client");
  const store = useScriptStore();

  // 发布场次页：只渲染候选（id 与 名称|类型）
  const Consumer = defineComponent({
    setup() {
      const s = useScriptStore();
      return () =>
        h(
          "ul",
          { class: "candidates" },
          s.activeScripts.value.map((script) =>
            h("li", { "data-id": script.id, class: "candidate" }, `${script.name}|${script.genre}`),
          ),
        );
    },
  });
  // 剧本管理页：暴露增/改/停/启动作，动作后 refresh 共享 store
  const Operator = defineComponent({
    setup() {
      const s = useScriptStore();
      return () =>
        h("div", {
          class: "operator",
          "data-count": s.scripts.value.length,
        });
    },
  });

  const wrapper = mount(defineComponent({
    components: { Consumer, Operator },
    render() {
      return h("div", [h(Operator), h(Consumer)]);
    },
  }));

  await store.load();
  await nextTick();

  const candidateNames = () =>
    wrapper.findAll(".candidate").map((node) => node.text().split("|")[0]);

  return { store, api, wrapper, candidateNames, server };
}

afterEach(() => {
  vi.restoreAllMocks();
  delete (globalThis as Partial<typeof globalThis>).fetch;
});

describe("跨组件/页面：剧本变更后场次候选无需刷新即同步", () => {
  it("新增 → 候选立即多一项", async () => {
    const { api, store, candidateNames } = await setupHarness(seedScripts());
    expect(candidateNames()).toEqual(["年轮", "拆迁", "古木吟", "病娇男孩的精分日记"]);

    await api.createScript({
      name: "新剧本Z", genre: "机制本", difficulty: "简单",
      durationMinutes: 180, minPlayers: 4, maxPlayers: 8,
    });
    await store.refresh();
    await nextTick();

    const names = candidateNames();
    expect(names).toHaveLength(5);
    expect(names).toContain("新剧本Z");
  });

  it("编辑 → 候选文本立即更新为新名称与类型", async () => {
    const { api, store, wrapper, candidateNames, server } = await setupHarness(seedScripts());
    const created = await api.createScript({
      name: "新剧本Z", genre: "机制本", difficulty: "简单",
      durationMinutes: 180, minPlayers: 4, maxPlayers: 8,
    });
    await store.refresh();
    await nextTick();

    await api.updateScript(created.id, {
      name: "新剧本Z-改名", genre: "阵营本", difficulty: "中等",
      durationMinutes: 200, minPlayers: 4, maxPlayers: 9,
    });
    await store.refresh();
    await nextTick();

    const names = candidateNames();
    expect(names).not.toContain("新剧本Z");
    expect(names).toContain("新剧本Z-改名");
    const node = wrapper.find(`[data-id="${created.id}"]`);
    expect(node.text()).toBe("新剧本Z-改名|阵营本");
    expect(server.db.find((s) => s.id === created.id)?.genre).toBe("阵营本");
  });

  it("停用 → 候选立即移除；启用 → 候选恢复", async () => {
    const { api, store, candidateNames } = await setupHarness(seedScripts());
    const created = await api.createScript({
      name: "新剧本Z", genre: "机制本", difficulty: "简单",
      durationMinutes: 180, minPlayers: 4, maxPlayers: 8,
    });
    await store.refresh();
    await nextTick();
    expect(candidateNames()).toHaveLength(5);

    await api.setScriptActive(created.id, false);
    await store.refresh();
    await nextTick();
    expect(candidateNames()).toHaveLength(4);
    expect(candidateNames()).not.toContain("新剧本Z");

    await api.setScriptActive(1, false);
    await store.refresh();
    await nextTick();
    expect(candidateNames()).toHaveLength(3);
    expect(candidateNames()).not.toContain("年轮");

    await api.setScriptActive(created.id, true);
    await api.setScriptActive(1, true);
    await store.refresh();
    await nextTick();
    expect(candidateNames()).toHaveLength(5);
    expect(candidateNames()).toContain("新剧本Z");
    expect(candidateNames()).toContain("年轮");
  });
});
