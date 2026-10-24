"""CLI regression for truncated UTF-8 and vendors that leave removed displays frozen."""
import json,os,signal,subprocess,tempfile,textwrap
from pathlib import Path
script=Path(__file__).resolve().parents[1]/'record-demo.sh'
with tempfile.TemporaryDirectory(prefix='shadowauto-recording-') as work:
 root=Path(work);bin_dir=root/'bin';bin_dir.mkdir()
 def executable(name,source):
  p=bin_dir/name;p.write_text('#!/usr/bin/env python3\n'+textwrap.dedent(source));p.chmod(0o755)
 executable('scrcpy',r'''
 import os,sys,time,signal
 from pathlib import Path
 if '--version' in sys.argv:print('scrcpy mock');raise SystemExit
 if '--help' in sys.argv:print('--display-id --record --window-title --no-control --no-audio --force-adb-forward --port');raise SystemExit
 signal.signal(signal.SIGINT,lambda *_:sys.exit(0))
 if '--display-id=7' in sys.argv:Path(os.environ['RECORD_TEST_CLOCK']).touch()
 while True:time.sleep(.1)
 ''')
 executable('adb',r'''
 import os,sys,time
 from pathlib import Path
 if 'logcat' in sys.argv:
  sys.stdout.buffer.write(b'broken UTF8: \xff\nVIRTUAL_DISPLAY_READY taskId=test-task displayId=7\n');sys.stdout.flush();time.sleep(7)
 else:
  clock=Path(os.environ['RECORD_TEST_CLOCK'])
  print('Display: mDisplayId=0')
  if not clock.exists() or time.time()-clock.stat().st_mtime<1:print('Display: mDisplayId=7')
 ''')
 env=os.environ|{'PATH':str(bin_dir)+os.pathsep+os.environ['PATH'],'RECORD_TEST_CLOCK':str(root/'clock')}
 p=subprocess.Popen([str(script),str(root/'output')],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True)
 try:
  stdout,stderr=p.communicate(timeout=15)
  assert p.returncode==0,stderr.decode(errors='replace')
  events=[json.loads(line) for line in (root/'output/timeline.jsonl').read_text().splitlines()]
  assert any(e['event']=='display_removed_stop_recording' and e['displayId']==7 for e in events),events
  assert events[-1]['event']=='recording_stopped',events
  print('record-demo CLI regression passed')
 finally:
  try:os.killpg(p.pid,signal.SIGKILL)
  except ProcessLookupError:pass
