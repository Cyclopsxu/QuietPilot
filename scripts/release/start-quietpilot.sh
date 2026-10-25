#!/system/bin/sh
# Run on the phone through adb shell, after uploading the shell APK and OCR.
set -eu
pid_file=/data/local/tmp/silent-auto.pid
if [ -f "$pid_file" ]; then
    previous_pid=$(cat "$pid_file")
    if kill -0 "$previous_pid" 2>/dev/null; then
        echo "Backend already running (PID $previous_pid). Finish active tasks before restarting."
        exit 1
    fi
fi
export CLASSPATH=/data/local/tmp/silent-shell.apk
nohup app_process /system/bin com.silentauto.shell.Main --port=43110 "$@" \
    >/data/local/tmp/silent-auto.log 2>&1 </dev/null &
backend_pid=$!
echo "$backend_pid" >"$pid_file"
sleep 1
if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "Backend exited. Inspect /data/local/tmp/silent-auto.log"
    exit 1
fi
echo "QuietPilot backend started (PID $backend_pid). Open the controller and test connection."
