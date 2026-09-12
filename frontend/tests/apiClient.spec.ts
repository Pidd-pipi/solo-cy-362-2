import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => {
  vi.restoreAllMocks();
  delete (globalThis as Partial<typeof globalThis>).fetch;
});

describe("API client：错误原因透传（页面据此向玩家说明原因）", () => {
  it("报名返回 400 时，错误消息为后端给出的中文原因", async () => {
    const clientMod = await import("../src/api/client");
    globalThis.fetch = vi.fn(async () => ({
      ok: false,
      status: 400,
      json: async () => ({ message: "该场次已满员（3/3），无法报名" }),
    })) as unknown as typeof fetch;

    await expect(
      clientMod.registerForSession(7, { playerName: "玩家D" }),
    ).rejects.toThrow("该场次已满员（3/3），无法报名");
  });

  it("重复报名的 400 原因被原样抛出", async () => {
    const clientMod = await import("../src/api/client");
    globalThis.fetch = vi.fn(async () => ({
      ok: false,
      status: 400,
      json: async () => ({ message: "玩家「玩家A」已报名该场次，不能重复报名" }),
    })) as unknown as typeof fetch;

    await expect(
      clientMod.registerForSession(1, { playerName: "玩家A" }),
    ).rejects.toThrow("已报名该场次，不能重复报名");
  });

  it("网络不可达时给出可理解的连接错误，而不是裸 TypeError", async () => {
    const clientMod = await import("../src/api/client");
    globalThis.fetch = vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    }) as unknown as typeof fetch;

    await expect(clientMod.fetchSessions()).rejects.toThrow("无法连接后端服务");
  });

  it("正常报名返回更新后的场次视图（剩余名额立即生效）", async () => {
    const clientMod = await import("../src/api/client");
    globalThis.fetch = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        id: 9, scriptId: 1, scriptName: "年轮", genre: "推理本", difficulty: "困难",
        startTime: "2099-01-01 14:00:00", hostName: "DM", capacity: 3,
        registeredCount: 1, remainingSlots: 2, full: false, registrations: [],
      }),
    })) as unknown as typeof fetch;

    const view = await clientMod.registerForSession(9, { playerName: "玩家A" });
    expect(view.remainingSlots).toBe(2);
    expect(view.registeredCount).toBe(1);
  });
});
