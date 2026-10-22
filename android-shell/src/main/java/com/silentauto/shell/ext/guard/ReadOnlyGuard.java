package com.silentauto.shell.ext.guard;

import com.silentauto.shell.ActionDecision;
import com.silentauto.shell.ext.skill.UiSnapshot;
import com.google.gson.JsonObject;
import java.util.*;

/** Query mode is enforced at the action boundary, including skill and raw-coordinate paths. */
public final class ReadOnlyGuard {
    private ReadOnlyGuard(){}
    public static final class Blocked extends IllegalStateException { public Blocked(String reason){super("READONLY_BLOCKED 查询任务只读，已拦截："+reason);} }
    public static void check(ActionDecision action,String dump){String reason=blocked(action,dump);if(!reason.isEmpty())throw new Blocked(reason);}
    public static boolean search(String value) {
        return value.toLowerCase(Locale.ROOT).matches("(?s).*(search|query|keyword|搜索|查找|检索).*");
    }
    private static String label(JsonObject n) {
        StringBuilder s=new StringBuilder();for(String k:new String[]{"text","desc","hint","id"})s.append(' ').append(UiSnapshot.text(n,k));return s.toString();
    }
    private static boolean forbidden(String text) {
        return text.matches("(?si).*(发送|提交|确认|支付|付款|下单|购买|加入购物车|删除|撤回|转发|分享|关注|点赞|评论|收藏).*")
                || java.util.regex.Pattern.compile("(?i)\\b(send|submit|pay|buy|delete)\\b").matcher(text).find();
    }
    private static JsonObject input(UiSnapshot ui,int index,boolean focused) {
        for(JsonObject n:ui.foreground().nodes)if(index>0?UiSnapshot.integer(n,"inputIndex")==index:focused&&UiSnapshot.bool(n,"editable")&&UiSnapshot.bool(n,"focused"))return n;
        return null;
    }
    public static String blocked(ActionDecision a,String dump) {
        UiSnapshot ui=new UiSnapshot(dump);String action=a.action;
        if(Arrays.asList("wait","scroll","back","report_answer","report_not_found").contains(action))return "";
        if("request_user".equals(action))return Arrays.asList("login","captcha").contains(a.kind)?"":"查询仅允许登录或验证码交接";
        if("drag".equals(action))return "";
        if("tap_target".equals(action)||"tap".equals(action)) {
            JsonObject selected=null;long size=Long.MAX_VALUE;
            for(JsonObject n:ui.foreground().nodes) {
                if(n.has("visible")&&!UiSnapshot.bool(n,"visible"))continue;
                if("tap_target".equals(action)&&a.targetIndex>0&&UiSnapshot.integer(n,"targetIndex")==a.targetIndex){selected=n;break;}
                if("tap".equals(action)&&UiSnapshot.bool(n,"clickable")) {
                    int[] b=UiSnapshot.bounds(n);long area=(long)(b[2]-b[0])*(b[3]-b[1]);
                    if(area>0&&area<size&&a.x>=b[0]&&a.x<b[2]&&a.y>=b[1]&&a.y<b[3]){selected=n;size=area;}
                }
            }
            return selected==null?"无法核实点击目标":forbidden(label(selected))?label(selected):"";
        }
        if(Arrays.asList("focus_input","text","clear_text","submit_search","key").contains(action)) {
            JsonObject field=input(ui,a.inputIndex,true);
            if(field==null||!search(label(field)))return "非搜索类输入框";
            if("key".equals(action)&&!Arrays.asList("enter","search").contains(a.key.toLowerCase(Locale.ROOT)))return "查询不允许该按键";
            return "";
        }
        return "查询不允许动作："+action;
    }
}
