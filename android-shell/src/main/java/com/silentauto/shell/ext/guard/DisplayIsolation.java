package com.silentauto.shell.ext.guard;
import com.google.gson.JsonObject;
/** Accepted configuration is recorded separately from behavioral validation. */
public final class DisplayIsolation {
 private DisplayIsolation(){}
 public static JsonObject assess(int flags,int candidate,int ownFocus,int noSteal,boolean localIme,boolean experimental){
  boolean own=ownFocus!=0&&(flags&ownFocus)==ownFocus,protect=noSteal!=0&&(flags&noSteal)==noSteal;
  boolean configured=own&&protect&&localIme;
  JsonObject o=new JsonObject();o.addProperty("selectedFlags",flags);o.addProperty("selectedFlagsHex",Integer.toHexString(flags));o.addProperty("candidateIndex",candidate);
  o.addProperty("ownFocusSupported",ownFocus!=0);o.addProperty("noStealSupported",noSteal!=0);o.addProperty("ownFocusRequested",own);o.addProperty("noStealRequested",protect);o.addProperty("localImeVerified",localIme);
  o.addProperty("configurationAvailable",configured);o.addProperty("behaviorValidated",false);o.addProperty("experimentalOverride",experimental&&!configured);o.addProperty("backgroundAllowed",configured||experimental);
  o.addProperty("status",configured?"configured_not_behavior_validated":experimental?"experimental_unverified":"blocked_insufficient_isolation");
  o.addProperty("message",configured?"隔离配置已接受，仍需实际并行输入/滚动验证":experimental?"兼容实验模式：隔离能力不足，不保证主屏输入和焦点不受影响":"设备无法确认独立焦点、禁止抢焦点或本地输入法，已阻止后台执行");return o;
 }
}
