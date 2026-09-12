<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { cancelRegistration, createSession, fetchScripts, fetchSessions, registerForSession } from "../api/client";
import type { Script, SessionForm, SessionView } from "../types";

const loading = ref(false);
const sessions = ref<SessionView[]>([]);
const activeScripts = ref<Script[]>([]);

const publishVisible = ref(false);
const publishing = ref(false);
const publishForm = reactive<SessionForm>({
  scriptId: null,
  startTime: defaultStartTime(),
  hostName: "",
  capacity: 6,
});
const publishError = ref("");

const registerVisible = ref(false);
const registering = ref(false);
const registerSessionId = ref<number | null>(null);
const registerSessionName = ref("");
const registerForm = reactive({ playerName: "", contact: "" });

function defaultStartTime(): string {
  const now = new Date();
  now.setDate(now.getDate() + 1);
  now.setHours(14, 0, 0, 0);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(
    now.getMinutes(),
  )}`;
}

async function loadAll() {
  loading.value = true;
  try {
    const [sessionList, scriptList] = await Promise.all([fetchSessions(), fetchScripts({})]);
    sessions.value = sessionList;
    activeScripts.value = scriptList;
  } catch (error) {
    ElMessage.error((error as Error).message || "加载场次失败");
  } finally {
    loading.value = false;
  }
}

function openPublish() {
  publishForm.scriptId = activeScripts.value[0]?.id ?? null;
  publishForm.startTime = defaultStartTime();
  publishForm.hostName = "";
  publishForm.capacity = activeScripts.value[0]?.maxPlayers ?? 6;
  publishError.value = "";
  publishVisible.value = true;
}

function onScriptChange(scriptId: number | null) {
  const script = activeScripts.value.find((item) => item.id === scriptId);
  if (script) {
    publishForm.capacity = script.maxPlayers;
  }
}

const selectedScript = computed(() =>
  activeScripts.value.find((item) => item.id === publishForm.scriptId),
);

async function submitPublish() {
  publishError.value = "";
  if (publishForm.scriptId === null) {
    publishError.value = "请选择剧本";
    return;
  }
  if (!publishForm.startTime) {
    publishError.value = "请选择开场时间";
    return;
  }
  if (!publishForm.hostName.trim()) {
    publishError.value = "请填写主持人";
    return;
  }
  if (!publishForm.capacity || publishForm.capacity < 1) {
    publishError.value = "人数上限至少为 1";
    return;
  }
  publishing.value = true;
  try {
    await createSession({
      scriptId: publishForm.scriptId,
      // datetime-local/手动输入均可能出现 "T" 分隔，后端两种格式都兼容
      startTime: publishForm.startTime,
      hostName: publishForm.hostName.trim(),
      capacity: publishForm.capacity,
    });
    ElMessage.success("场次发布成功");
    publishVisible.value = false;
    await loadAll();
  } catch (error) {
    // 发布失败时在弹窗内说明原因，保留用户已填内容
    publishError.value = (error as Error).message || "发布失败";
  } finally {
    publishing.value = false;
  }
}

function openRegister(session: SessionView) {
  registerSessionId.value = session.id;
  registerSessionName.value = session.scriptName;
  registerForm.playerName = "";
  registerForm.contact = "";
  registerVisible.value = true;
}

async function submitRegister() {
  if (!registerForm.playerName.trim()) {
    ElMessage.error("请填写玩家昵称");
    return;
  }
  registering.value = true;
  try {
    const updated = await registerForSession(registerSessionId.value as number, {
      playerName: registerForm.playerName.trim(),
      contact: registerForm.contact.trim() || undefined,
    });
    sessions.value = sessions.value.map((item) => (item.id === updated.id ? updated : item));
    ElMessage.success(`报名成功，剩余名额 ${updated.remainingSlots}`);
    registerVisible.value = false;
  } catch (error) {
    ElMessage.error((error as Error).message || "报名失败");
  } finally {
    registering.value = false;
  }
}

async function cancel(session: SessionView, playerName: string) {
  try {
    await ElMessageBox.confirm(
      `确认取消玩家「${playerName}」在《${session.scriptName}》(${session.startTime}) 的报名吗？取消后空位立即可被再次报名。`,
      "取消报名",
      { type: "warning", confirmButtonText: "确认取消", cancelButtonText: "再想想" },
    );
  } catch {
    return;
  }
  try {
    const updated = await cancelRegistration(session.id, playerName);
    sessions.value = sessions.value.map((item) => (item.id === updated.id ? updated : item));
    ElMessage.success(`已取消报名，剩余名额 ${updated.remainingSlots}`);
  } catch (error) {
    ElMessage.error((error as Error).message || "取消失败");
  }
}

function progressPercentage(session: SessionView): number {
  if (session.capacity === 0) return 0;
  return Math.min(100, Math.round((session.registeredCount / session.capacity) * 100));
}

function progressStatus(session: SessionView): "" | "success" | "warning" | "exception" {
  if (session.full) return "exception";
  if (session.remainingSlots <= 2) return "warning";
  return "success";
}

function formatDateTime(value: string): string {
  return value.replace("T", " ").slice(0, 16);
}

onMounted(loadAll);
</script>

<template>
  <section class="panel-card">
    <div class="toolbar">
      <p class="panel-hint">
        每场实时显示已报名人数与剩余空位；报名后名额立即扣减，满员不可报名，取消后空位可再次使用。
      </p>
      <el-button type="primary" plain :disabled="activeScripts.length === 0" @click="openPublish">
        ＋ 发布场次
      </el-button>
    </div>

    <el-alert
      v-if="activeScripts.length === 0"
      title="暂无启用中的剧本，请先到「剧本管理」新增剧本后再发布场次。"
      type="info"
      :closable="false"
      class="empty-alert"
    />

    <el-table v-loading="loading" :data="sessions" border stripe class="data-table">
      <el-table-column label="剧本" min-width="200">
        <template #default="{ row }">
          <div class="session-title">
            <span class="script-name">《{{ row.scriptName }}》</span>
            <span class="tag-row">
              <el-tag size="small">{{ row.genre }}</el-tag>
              <el-tag size="small" type="info">{{ row.difficulty }}</el-tag>
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="开场时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column prop="hostName" label="主持人" width="120" />
      <el-table-column label="报名进度" min-width="220">
        <template #default="{ row }">
          <div class="progress-cell">
            <el-progress :percentage="progressPercentage(row)" :status="progressStatus(row)" :stroke-width="14" />
            <span class="progress-text">
              已报名 <b>{{ row.registeredCount }}</b> / {{ row.capacity }}，剩余
              <b :class="{ 'slots-full': row.full }">{{ row.remainingSlots }}</b> 位
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="报名玩家" min-width="220">
        <template #default="{ row }">
          <div class="player-tags">
            <el-tag
              v-for="registration in row.registrations"
              :key="registration.id"
              closable
              class="player-tag"
              @close="cancel(row, registration.playerName)"
            >
              {{ registration.playerName }}
            </el-tag>
            <span v-if="row.registrations.length === 0" class="muted-text">暂无报名，快来拼车</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.full"
            type="danger"
            size="small"
            disabled
          >
            已满员
          </el-button>
          <el-button v-else type="primary" size="small" @click="openRegister(row)">我要报名</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="publishVisible" title="发布场次" width="520px">
      <el-form label-width="100px">
        <el-form-item label="剧本" required>
          <el-select
            v-model="publishForm.scriptId"
            placeholder="请选择剧本"
            style="width: 100%"
            @change="onScriptChange"
          >
            <el-option
              v-for="script in activeScripts"
              :key="script.id"
              :label="`《${script.name}》（${script.genre} / ${script.minPlayers}-${script.maxPlayers}人）`"
              :value="script.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开场时间" required>
          <el-date-picker
            v-model="publishForm.startTime"
            type="datetime"
            placeholder="选择开场时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="(date: Date) => date.getTime() < Date.now() - 24 * 3600 * 1000"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="主持人" required>
          <el-input v-model="publishForm.hostName" maxlength="80" placeholder="例如：DM-小鹿" />
        </el-form-item>
        <el-form-item label="人数上限" required>
          <el-input-number v-model="publishForm.capacity" :min="1" :max="30" />
          <span v-if="selectedScript" class="form-hint">
            该剧本建议 {{ selectedScript.minPlayers }}-{{ selectedScript.maxPlayers }} 人
          </span>
        </el-form-item>
      </el-form>
      <el-alert v-if="publishError" :title="publishError" type="error" :closable="false" class="form-alert" />
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitPublish">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="registerVisible" :title="`报名拼车：${registerSessionName}`" width="440px">
      <el-form label-width="90px">
        <el-form-item label="玩家昵称" required>
          <el-input
            v-model="registerForm.playerName"
            maxlength="80"
            placeholder="用于核对报名与取消"
            @keyup.enter="submitRegister"
          />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input
            v-model="registerForm.contact"
            maxlength="120"
            placeholder="手机号/微信（选填，便于开场提醒）"
            @keyup.enter="submitRegister"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="submitRegister">确认报名</el-button>
      </template>
    </el-dialog>
  </section>
</template>
