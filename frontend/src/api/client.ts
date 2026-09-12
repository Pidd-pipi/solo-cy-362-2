import { API_BASE_URL } from "../constants/app";
import type { OverviewResponse } from "../types";

/** 后端错误体统一为 { message: string }，网络异常也转成可读原因。 */
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers: { Accept: "application/json", ...(options?.body ? { "Content-Type": "application/json" } : {}) },
      ...options,
    });
  } catch {
    throw new Error("无法连接后端服务，请确认后端已启动");
  }

  if (!response.ok) {
    let message = `请求失败（HTTP ${response.status}）`;
    try {
      const data = (await response.json()) as { message?: string };
      if (data.message) {
        message = data.message;
      }
    } catch {
      // 非 JSON 错误体时保留默认消息
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export async function fetchOverview(): Promise<OverviewResponse> {
  return request<OverviewResponse>("/overview");
}

export function fetchScripts(params: {
  name?: string;
  genre?: string;
  difficulty?: string;
  includeInactive?: boolean;
}): Promise<import("../types").Script[]> {
  const search = new URLSearchParams();
  if (params.name) search.set("name", params.name);
  if (params.genre) search.set("genre", params.genre);
  if (params.difficulty) search.set("difficulty", params.difficulty);
  if (params.includeInactive) search.set("includeInactive", "true");
  const suffix = search.toString() ? `?${search.toString()}` : "";
  return request(`/scripts${suffix}`);
}

export function createScript(body: import("../types").ScriptForm): Promise<import("../types").Script> {
  return request("/scripts", { method: "POST", body: JSON.stringify(body) });
}

export function updateScript(
  id: number,
  body: import("../types").ScriptForm,
): Promise<import("../types").Script> {
  return request(`/scripts/${id}`, { method: "PUT", body: JSON.stringify(body) });
}

export function setScriptActive(id: number, active: boolean): Promise<import("../types").Script> {
  return request(`/scripts/${id}/active`, { method: "PATCH", body: JSON.stringify({ active }) });
}

export function fetchSessions(): Promise<import("../types").SessionView[]> {
  return request("/sessions");
}

export function createSession(body: import("../types").SessionForm): Promise<import("../types").SessionView> {
  return request("/sessions", { method: "POST", body: JSON.stringify(body) });
}

export function registerForSession(
  sessionId: number,
  body: import("../types").RegistrationForm,
): Promise<import("../types").SessionView> {
  return request(`/sessions/${sessionId}/registrations`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function cancelRegistration(
  sessionId: number,
  playerName: string,
): Promise<import("../types").SessionView> {
  return request(
    `/sessions/${sessionId}/registrations?playerName=${encodeURIComponent(playerName)}`,
    { method: "DELETE" },
  );
}
