#!/usr/bin/env bash
# 剧本排期与拼车报名：一键运行全部自动化测试（不依赖外部数据库/网络/人工操作）。
#   - 后端：SpringBootTest + MockMvc + 内嵌 H2（含多线程并发抢名额）
#   - 前端：Vitest + happy-dom（候选联动与报错透传）
# 用法： ./run-tests.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 允许通过环境变量指定 Java/Maven，否则使用 PATH 中的版本
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in "$HOME/opt/jdk-17"*; do
    [ -d "$candidate" ] && export JAVA_HOME="$candidate" && break
  done
fi

echo "==================== 1/2 后端测试（JUnit5 + MockMvc + H2） ===================="
( cd "$ROOT_DIR/backend" && mvn -q test )

echo
echo "==================== 2/2 前端测试（Vitest + happy-dom） ========================"
( cd "$ROOT_DIR/frontend" && npm test --silent )

echo
echo "全部测试通过。"
