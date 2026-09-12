import { computed, ref } from "vue";
import { fetchScripts } from "../api/client";
import type { Script } from "../types";

/**
 * 剧本共享状态：剧本管理页与场次发布页使用同一份数据。
 * 剧本新增/编辑/停用/启用后调用 refresh()，场次候选（activeScripts）立即联动，
 * 无需刷新整个页面；停用剧本自动从候选中消失，重新启用后自动恢复。
 */
const scripts = ref<Script[]>([]);
let inflight: Promise<void> | null = null;
let loaded = false;

async function load(force = false): Promise<void> {
  if (loaded && !force) {
    return;
  }
  if (inflight) {
    return inflight;
  }
  const task = (async () => {
    const list = await fetchScripts({ includeInactive: true });
    scripts.value = list;
    loaded = true;
  })();
  inflight = task.finally(() => {
    inflight = null;
  });
  return inflight;
}

/** 剧本管理页执行增/改/停用/启用后调用，强制重新拉取全量（含停用）剧本。 */
function refresh(): Promise<void> {
  return load(true);
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
