#!/usr/bin/env bash
set -euo pipefail
# Pass adb -s through ANDROID_SERIAL; run before starting the first task.
command -v adb >/dev/null || { echo 'adb is required' >&2; exit 1; }
command -v scrcpy >/dev/null || { echo 'scrcpy is required; install it and rerun' >&2; exit 1; }
exec python3 - "${1:-demo-recordings}" <<'PY'
import datetime,json,os,re,signal,subprocess,sys,time
from pathlib import Path
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=False)
version=subprocess.check_output(['scrcpy','--version'],text=True)
help_text=subprocess.check_output(['scrcpy','--help'],text=True)
for flag in ['--display-id','--record','--window-title','--no-control','--no-audio','--force-adb-forward','--port']:
 if flag not in help_text:raise SystemExit('Unsupported scrcpy option: '+flag)
(out/'scrcpy-version.txt').write_text(version)
processes=[];handles=[];seen=set()
manifest=(out/'timeline.jsonl').open('a',buffering=1)
def event(kind,**fields):
 manifest.write(json.dumps(dict(event=kind,utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),hostMonotonicNs=time.monotonic_ns(),**fields),ensure_ascii=False)+'\n')
def launch(display,title,filename):
 handle=(out/(filename+'.log')).open('w');handles.append(handle)
 event('recorder_start_requested',displayId=display,file=filename)
 process=subprocess.Popen(['scrcpy','--force-adb-forward','--port='+str(27200+len(processes)),'--display-id='+str(display),'--window-title='+title,'--no-control','--no-audio','--record='+str(out/filename)],stdout=handle,stderr=subprocess.STDOUT)
 processes.append(process);return process
def stop(*_):raise KeyboardInterrupt
signal.signal(signal.SIGTERM,stop)
logs=None
try:
 launch(0,'主屏','main.mp4')
 # -T 1 starts from the most recent entry rather than replaying the whole log buffer.
 logs=subprocess.Popen(['adb','logcat','-v','epoch','-T','1','-s','ShadowAutoShell:I','*:S'],stdout=subprocess.PIPE,text=True,errors='replace',bufsize=1)
 event('waiting_for_virtual_display')
 for line in logs.stdout:
  match=re.search(r'VIRTUAL_DISPLAY_READY taskId=([\w.-]+) displayId=(\d+)',line)
  if not match:continue
  task,display=match[1],int(match[2]);identity=(task,display)
  if identity in seen:continue
  seen.add(identity);event('virtual_display_ready',taskId=task,displayId=display,deviceLog=line.strip())
  launch(display,'虚拟屏 '+task,task+'-vd.mp4')
  # A removed display ends its scrcpy server; continue listening for the next task.
  for process in processes:
   if process.poll() is not None:event('recorder_exited',pid=process.pid,returncode=process.returncode)
except KeyboardInterrupt:pass
finally:
 if logs is not None:logs.terminate()
 for process in processes:
  if process.poll() is None:process.send_signal(signal.SIGINT)
 for process in processes:
  try:process.wait(timeout=10)
  except subprocess.TimeoutExpired:process.terminate()
 event('recording_stopped')
 for handle in handles:handle.close()
 manifest.close()
PY
