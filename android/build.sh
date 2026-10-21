#!/usr/bin/env bash
# 二课++ 构建入口（Git Bash / CI 使用；Windows 日常请用 build.cmd）
set -euo pipefail
cd "$(dirname "$0")"

JDK="${ERKE_JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
if [ ! -x "$JDK/bin/java" ]; then
    echo "未找到可用 JDK：$JDK，请设置 ERKE_JAVA_HOME 指向 JDK 17+" >&2
    exit 1
fi
export JAVA_HOME="$JDK"

# 签名目录：环境变量优先，否则用本机约定位置
export ERKE_KS_DIR="${ERKE_KS_DIR:-$HOME/.android/keystore}"

GRADLE_TASK="${1:-assembleDebug}"
exec ./gradlew "$GRADLE_TASK" --console=plain
