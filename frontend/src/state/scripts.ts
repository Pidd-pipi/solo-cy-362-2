import { computed, ref } from "vue";
import { fetchScripts } from "../api/client";
import type { Script } from "../types";

/**
 * 剧本共享状态：剧本管理页与场次发布页使用同一份数据。
 * 剧本新增/编辑/停用/启用后调用 refresh()，场次候选（activeScripts）立即联动，
 * 无需刷新整个页面；停用剧本自动从候选中消失，重新启用后自动恢复。
 *
 * 刷新竞态处理：
 * 1. 保存触发的 refresh() 会推进版本号（wantVersion），flush 时发现“在途请求版本更旧”
 *    会发起新请求，绝不复用保存前就已发出的旧请求，最终候选一定是保存后的最新状态。
 * 2. 同一微任务内并发 refresh() 通过微任务调度合并为一次请求。
 * 3. 每次发请求领取递增 token；响应回来时只有最新 token 允许写入，乱序返回不会覆盖较新结果。
 * 4. 请求失败只拒绝当前等待者并清理在途状态，下一次 load/refresh 可重新发起，能够恢复。
 */
const scripts = ref<Script[]>([]);

/** 已被等待的最高版本；refresh() 表示“刚发生一次保存”，版本号 +1。 */
let wantVersion = 0;
/** 已成功写入 scripts 的版本；-1 表示从未加载成功。 */
let appliedVersion = -1;
/** 已发出的在途请求中的最高版本，用于判断是否可以复用在途请求。 */
let inflightVersion = -1;
/** 每次发请求递增，仅 token === latestToken 的响应允许落地，防止乱序覆盖。 */
let latestToken = 0;
let flushQueued = false;

interface Waiter {
  version: number;
  resolve: () => void;
  reject: (error: unknown) => void;
}
const waiters: Waiter[] = [];

function fulfillWaiters() {
  // 从后往前删除，避免索引位移；满足“所需版本 <= 已应用版本”的等待者全部放行
  for (let i = waiters.length - 1; i >= 0; i -= 1) {
    if (waiters[i].version <= appliedVersion) {
      const [waiter] = waiters.splice(i, 1);
      waiter.resolve();
    }
  }
}

function rejectWaiters(error: unknown) {
  const pending = waiters.splice(0);
  pending.forEach((waiter) => waiter.reject(error));
}

/** 等待数据版本不低于 version；注册等待者后再调度请求，避免漏掉结果。 */
function request(version: number): Promise<void> {
  if (appliedVersion >= version) {
    return Promise.resolve();
  }
  const promise = new Promise<void>((resolve, reject) => {
    waiters.push({ version, resolve, reject });
  });
  scheduleFlush();
  return promise;
}

/** 微任务合并：同一 tick 内的多次 load/refresh 只 flush 一次。 */
function scheduleFlush() {
  if (flushQueued) {
    return;
  }
  flushQueued = true;
  queueMicrotask(() => {
    flushQueued = false;
    flush();
  });
}

/** 已应用或已有足够新的在途请求则合并复用；否则必须发新请求（不复用保存前的旧请求）。 */
function flush() {
  if (appliedVersion >= wantVersion || inflightVersion >= wantVersion) {
    return;
  }
  launch(wantVersion);
}

async function launch(version: number) {
  const token = ++latestToken;
  inflightVersion = Math.max(inflightVersion, version);
  try {
    const list = await fetchScripts({ includeInactive: true });
    // 乱序防护：期间又有更新的请求发出时，本次（旧）响应直接丢弃，绝不覆盖较新结果
    if (token !== latestToken) {
      return;
    }
    scripts.value = list;
    appliedVersion = version;
    inflightVersion = -1;
    fulfillWaiters();
  } catch (error) {
    // 过期请求失败不影响任何人（等待者由更新的请求负责）；只有最新请求失败才向外抛错
    if (token !== latestToken) {
      return;
    }
    inflightVersion = -1;
    rejectWaiters(error);
  }
}

/** 首次加载：多个调用方并发时合并到同一请求；已加载过则立即返回。 */
function load(): Promise<void> {
  return request(wantVersion);
}

/**
 * 剧本管理页执行新增/编辑/停用/启用成功后调用：
 * 版本号 +1 表示“需要严格新于任何在途请求的数据”，随后合并同 tick 的并发刷新。
 */
function refresh(): Promise<void> {
  wantVersion += 1;
  return request(wantVersion);
}

/** 场次发布候选：仅启用中的剧本，顺序与剧本管理页一致（启用在前、新剧在前）。 */
const activeScripts = computed(() => scripts.value.filter((script) => script.active));

export function useScriptStore() {
  return {
    scripts,
    activeScripts,
    load,
    refresh,
  };
}
