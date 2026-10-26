#!/bin/bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d /private/tmp/shadowauto-interaction-regression.XXXXXX)"
trap 'rm -rf "$work"' EXIT
# Reuse the dependency resolved by the Android build; never fetch test dependencies.
gson="$(rg --files "$HOME/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0" -g 'gson-2.11.0.jar' | head -n 1)"
classes="$repo/android-shell/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
[ -d "$classes" ] || { echo 'Run :android-shell:assembleDebug first' >&2; exit 1; }
javac -d "$work" -cp "$classes:$gson" "$repo/scripts/tests/LoopDetectorRegression.java" "$repo/scripts/tests/UiRecoveryRegression.java" "$repo/scripts/tests/InteractionRegression.java" "$repo/scripts/tests/TransferProbe.java" "$repo/scripts/tests/QueryProbe.java" "$repo/scripts/tests/RpcOutputRegression.java"
javac -d "$work" "$repo/controller-app/src/main/java/com/silentauto/controller/RpcConnection.java" "$repo/scripts/tests/RpcConnectionRegression.java"
java -cp "$work" RpcConnectionRegression
java -cp "$work:$classes:$gson" UiRecoveryRegression
java -cp "$work:$classes:$gson" InteractionRegression
java -cp "$work:$classes:$gson" com.silentauto.shell.ext.tests.TransferProbe
java -cp "$work:$classes:$gson" com.silentauto.shell.ext.tests.QueryProbe
java -cp "$work:$classes:$gson" com.silentauto.shell.RpcOutputRegression

javac -d "$work" -cp "$classes:$gson" "$repo/scripts/tests/ScreenLockRegression.java"
java -cp "$work:$classes:$gson" ScreenLockRegression
javac -d "$work" -cp "$classes:$gson" "$repo/scripts/tests/android/os/SystemClock.java" "$repo/scripts/tests/VisualInputRegression.java"
java -cp "$work:$classes:$gson" VisualInputRegression
javac -d "$work" -cp "$classes:$gson" "$repo/scripts/tests/MainInputIdleRegression.java"
java -cp "$work:$classes:$gson" MainInputIdleRegression
javac -d "$work" -cp "$classes:$gson" "$repo/scripts/tests/StepBudgetHandoffRegression.java"
java -cp "$work:$classes:$gson" StepBudgetHandoffRegression
java -cp "$work:$classes:$gson" LoopDetectorRegression

node "$repo/scripts/tests/isolation-report.test.mjs"
python3 "$repo/scripts/tests/record-demo.test.py"
