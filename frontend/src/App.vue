<script setup lang="ts">
import { onMounted, ref } from "vue";
import { fetchOverview } from "./api/client";
import { APP_CODE, APP_NAME } from "./constants/app";
import { REQUEST_MESSAGES } from "./constants/messages";
import { createFallbackOverview } from "./state/dashboard";
import type { OverviewResponse } from "./types";
import FeatureStrip from "./components/FeatureStrip.vue";
import MetricGrid from "./components/MetricGrid.vue";
import OperationsTable from "./components/OperationsTable.vue";
import ScriptManager from "./components/ScriptManager.vue";
import SessionSchedule from "./components/SessionSchedule.vue";

const overview = ref<OverviewResponse>(createFallbackOverview());
const notice = ref(REQUEST_MESSAGES.overviewFallback);
const activeTab = ref("sessions");

function goHealth() {
  window.location.href = REQUEST_MESSAGES.healthPath;
}

onMounted(async () => {
  try {
    overview.value = await fetchOverview();
    notice.value = "后端服务已联通，当前展示实时接口数据。";
  } catch {
    notice.value = REQUEST_MESSAGES.overviewFallback;
  }
});
</script>

<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <span class="brand-code">{{ APP_CODE }}</span>
        <h1 class="brand-title">{{ APP_NAME }}</h1>
      </div>
      <el-button type="primary" @click="goHealth">API Health</el-button>
    </header>
    <section class="workspace">
      <el-tabs v-model="activeTab" class="module-tabs">
        <el-tab-pane label="场次排期与拼车报名" name="sessions">
          <SessionSchedule />
        </el-tab-pane>
        <el-tab-pane label="剧本管理" name="scripts">
          <ScriptManager />
        </el-tab-pane>
        <el-tab-pane label="运营总览" name="overview">
          <div class="lead-grid">
            <article class="hero-panel">
              <span class="pill">{{ notice }}</span>
              <h2>{{ overview.appName }}</h2>
              <p>{{ overview.description }}</p>
            </article>
            <MetricGrid :items="overview.kpis" />
          </div>
          <FeatureStrip :items="overview.features" />
          <section class="work-panel">
            <h2>运营任务流</h2>
            <OperationsTable :records="overview.records" />
          </section>
        </el-tab-pane>
      </el-tabs>
    </section>
  </main>
</template>
