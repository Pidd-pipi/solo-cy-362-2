<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { createScript, setScriptActive, updateScript } from "../api/client";
import { DIFFICULTY_OPTIONS, GENRE_OPTIONS } from "../constants/options";
import { useScriptStore } from "../state/scripts";
import type { Script, ScriptForm } from "../types";

const scriptStore = useScriptStore();
const loading = ref(false);

const filters = reactive({
  name: "",
  genre: "",
  difficulty: "",
  includeInactive: true,
});

/** 表格数据直接派生自共享 store：候选与场次发布页永远看到同一份剧本状态。 */
const scripts = computed(() =>
  scriptStore.scripts.value.filter((script) => {
    if (!filters.includeInactive && !script.active) return false;
    if (filters.name && !script.name.toLowerCase().includes(filters.name.trim().toLowerCase())) return false;
    if (filters.genre && script.genre !== filters.genre) return false;
    if (filters.difficulty && script.difficulty !== filters.difficulty) return false;
    return true;
  }),
);

const dialogVisible = ref(false);
const dialogTitle = ref("新增剧本");
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formRef = ref<FormInstance>();

const emptyForm = (): ScriptForm => ({
  name: "",
  genre: GENRE_OPTIONS[0],
  difficulty: DIFFICULTY_OPTIONS[1],
  durationMinutes: 240,
  minPlayers: 4,
  maxPlayers: 8,
  dmRequirement: "",
  description: "",
});

const form = reactive<ScriptForm>(emptyForm());

const rules: FormRules<ScriptForm> = {
  name: [{ required: true, message: "请输入剧本名称", trigger: "blur" }],
  genre: [{ required: true, message: "请选择类型", trigger: "change" }],
  difficulty: [{ required: true, message: "请选择难度", trigger: "change" }],
  durationMinutes: [{ required: true, message: "请输入时长（分钟）", trigger: "blur" }],
  minPlayers: [{ required: true, message: "请输入最少人数", trigger: "blur" }],
  maxPlayers: [{ required: true, message: "请输入人数上限", trigger: "blur" }],
};

// 筛选为本地派生，查询/重置只更新条件；网络加载统一走 store
function loadScripts() {
  return scriptStore.load();
}

function resetFilters() {
  filters.name = "";
  filters.genre = "";
  filters.difficulty = "";
  filters.includeInactive = true;
}

function openCreate() {
  editingId.value = null;
  dialogTitle.value = "新增剧本";
  Object.assign(form, emptyForm());
  dialogVisible.value = true;
}

function openEdit(script: Script) {
  editingId.value = script.id;
  dialogTitle.value = `编辑剧本：${script.name}`;
  Object.assign(form, {
    name: script.name,
    genre: script.genre,
    difficulty: script.difficulty,
    durationMinutes: script.durationMinutes,
    minPlayers: script.minPlayers,
    maxPlayers: script.maxPlayers,
    dmRequirement: script.dmRequirement ?? "",
    description: script.description ?? "",
  });
  dialogVisible.value = true;
}

async function submitForm() {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  if (form.minPlayers > form.maxPlayers) {
    ElMessage.error("最少人数不能大于人数上限");
    return;
  }
  submitting.value = true;
  try {
    if (editingId.value === null) {
      await createScript({ ...form });
      ElMessage.success("剧本新增成功");
    } else {
      await updateScript(editingId.value, { ...form });
      ElMessage.success("剧本已更新");
    }
    dialogVisible.value = false;
    // 刷新共享 store：表格与场次发布候选同步更新，无需刷新页面
    await scriptStore.refresh();
  } catch (error) {
    ElMessage.error((error as Error).message || "保存失败");
  } finally {
    submitting.value = false;
  }
}

async function toggleActive(script: Script) {
  const nextActive = !script.active;
  try {
    await ElMessageBox.confirm(
      nextActive
        ? `确认启用剧本「${script.name}」吗？启用后可发布新场次。`
        : `确认停用剧本「${script.name}」吗？停用后不能再发布新场次，历史场次保留。`,
      nextActive ? "启用剧本" : "停用剧本",
      { type: "warning", confirmButtonText: "确认", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await setScriptActive(script.id, nextActive);
    ElMessage.success(nextActive ? "剧本已启用" : "剧本已停用");
    await scriptStore.refresh();
  } catch (error) {
    ElMessage.error((error as Error).message || "状态更新失败");
  }
}

function difficultyTagType(difficulty: string): "success" | "warning" | "danger" {
  if (difficulty === "简单") return "success";
  if (difficulty === "中等") return "warning";
  return "danger";
}

onMounted(async () => {
  loading.value = true;
  try {
    await loadScripts();
  } catch (error) {
    ElMessage.error((error as Error).message || "加载剧本列表失败");
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="panel-card">
    <div class="toolbar">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="名称">
          <el-input
            v-model="filters.name"
            placeholder="按剧本名称搜索"
            clearable
            style="width: 180px"
            @keyup.enter="loadScripts"
            @clear="loadScripts"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.genre" placeholder="全部类型" clearable style="width: 140px">
            <el-option v-for="genre in GENRE_OPTIONS" :key="genre" :label="genre" :value="genre" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="filters.difficulty" placeholder="全部难度" clearable style="width: 130px">
            <el-option
              v-for="difficulty in DIFFICULTY_OPTIONS"
              :key="difficulty"
              :label="difficulty"
              :value="difficulty"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadScripts">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
      <el-button type="primary" plain @click="openCreate">＋ 新增剧本</el-button>
    </div>

    <el-table v-loading="loading" :data="scripts" border stripe class="data-table">
      <el-table-column prop="name" label="剧本名称" min-width="180" />
      <el-table-column prop="genre" label="类型" width="100" />
      <el-table-column label="难度" width="90">
        <template #default="{ row }">
          <el-tag :type="difficultyTagType(row.difficulty)">{{ row.difficulty }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时长" width="100">
        <template #default="{ row }">{{ row.durationMinutes }} 分钟</template>
      </el-table-column>
      <el-table-column label="建议人数" width="110">
        <template #default="{ row }">{{ row.minPlayers }}-{{ row.maxPlayers }} 人</template>
      </el-table-column>
      <el-table-column prop="dmRequirement" label="DM 要求" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.dmRequirement || "—" }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? "启用中" : "已停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            :type="row.active ? 'danger' : 'success'"
            size="small"
            @click="toggleActive(row)"
          >
            {{ row.active ? "停用" : "启用" }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="剧本名称" prop="name">
          <el-input v-model="form.name" maxlength="120" show-word-limit placeholder="例如：年轮" />
        </el-form-item>
        <el-form-item label="类型" prop="genre">
          <el-select v-model="form.genre" style="width: 100%">
            <el-option v-for="genre in GENRE_OPTIONS" :key="genre" :label="genre" :value="genre" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-radio-group v-model="form.difficulty">
            <el-radio-button v-for="level in DIFFICULTY_OPTIONS" :key="level" :value="level">
              {{ level }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="时长（分钟）" prop="durationMinutes">
          <el-input-number v-model="form.durationMinutes" :min="1" :max="1440" :step="10" />
        </el-form-item>
        <el-form-item label="建议人数" prop="minPlayers">
          <div class="player-range">
            <el-input-number v-model="form.minPlayers" :min="1" :max="30" />
            <span class="range-sep">至</span>
            <el-input-number v-model="form.maxPlayers" :min="1" :max="30" />
            <span class="range-sep">人</span>
          </div>
        </el-form-item>
        <el-form-item label="DM 要求">
          <el-input v-model="form.dmRequirement" maxlength="120" placeholder="例如：资深DM（控场/计时）" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="剧本背景与特色简介"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
