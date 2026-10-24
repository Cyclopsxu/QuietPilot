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
        return text.matches("(?si).*(预订|预定|订座|预约|开始导航|开始实景导航|开始骑行|navi_agreement_agree_btn|立即打车|去打车|叫车|呼叫|发送|提交|确认|支付|付款|下单|购买|加入购物车|删除|撤回|转发|分享|关注|点赞|评论|收藏).*")
                || java.util.regex.Pattern.compile("(?i)\\b(send|submit|pay|buy|delete)\\b").matcher(text).find();
    }
    private static JsonObject input(UiSnapshot ui,int index,boolean focused) {
        for(JsonObject n:ui.foreground().nodes)if(index>0?UiSnapshot.integer(n,"inputIndex")==index:focused&&UiSnapshot.bool(n,"editable")&&UiSnapshot.bool(n,"focused"))return n;
        return null;
    }
    private static boolean routeInput(UiSnapshot ui,JsonObject field) {
        if(!"com.autonavi.minimap".equals(UiSnapshot.text(field,"package"))
                || !UiSnapshot.bool(field,"editable") || UiSnapshot.bool(field,"password"))return false;
        // Amap's anonymous EditTexts carry the endpoint name, while the enclosing
        // accessibility group describes their role. Bind to that role and bounds,
        // never to action.reason or a page-wide occurrence of '起点'.
        for(JsonObject n:ui.foreground().nodes) {
            if(!"com.autonavi.minimap".equals(UiSnapshot.text(n,"package"))
                    || (n.has("visible")&&!UiSnapshot.bool(n,"visible")))continue;
            String role=(UiSnapshot.text(n,"text")+" "+UiSnapshot.text(n,"desc")+" "+UiSnapshot.text(n,"label")).trim();
            if(!role.matches("^(起点|终点)[，,：:].*") || role.contains("切换起终点")
                    || role.contains("添加途经点") || (role.contains("起点")&&role.contains("终点")))continue;
            int[] f=UiSnapshot.bounds(field),b=UiSnapshot.bounds(n);
            if(f[2]>f[0]&&f[3]>f[1]&&b[0]<=f[0]&&b[1]<=f[1]&&b[2]>=f[2]&&b[3]>=f[3])return true;
        }
        return false;
    }
    private static boolean amapSearchInput(UiSnapshot ui,JsonObject field) {
        if(!"com.autonavi.minimap".equals(UiSnapshot.text(field,"package"))
                ||!UiSnapshot.bool(field,"editable")||UiSnapshot.bool(field,"password")
                ||!UiSnapshot.bool(field,"visible"))return false;
        int[] f=UiSnapshot.bounds(field);int h=f[3]-f[1];
        if(h<=0||f[2]<=f[0])return false;
        boolean bar=false,submit=false;
        for(JsonObject n:ui.foreground().nodes) {
            if(!"com.autonavi.minimap".equals(UiSnapshot.text(n,"package"))
                    ||!UiSnapshot.bool(n,"visible"))continue;
            int[] b=UiSnapshot.bounds(n);
            // Duplicate accessibility representations at the same bounds are common;
            // a second distinct editor makes this anonymous search form ambiguous.
            if(UiSnapshot.bool(n,"editable")&&!Arrays.equals(b,f))return false;
            if("com.autonavi.minimap.ajx3.widget.view.SpringHorizontalScrollView".equals(UiSnapshot.text(n,"class"))
                    &&b[0]<=f[0]&&b[2]>=f[2]&&b[1]==f[1]&&b[3]==f[3])bar=true;
            String role=(UiSnapshot.text(n,"text")+UiSnapshot.text(n,"desc")).trim();
            if(role.equals("搜索")&&UiSnapshot.integer(n,"targetIndex")>0
                    &&b[0]>=f[0]&&b[2]<=f[2]&&b[1]>=f[3]&&b[1]-f[3]<=2*h
                    &&b[3]>b[1]&&b[3]-b[1]<=2*h)submit=true;
        }
        return bar&&submit;
    }
    private static boolean meituanLocationInput(UiSnapshot ui,JsonObject field) {
        if(!"com.sankuai.meituan".equals(UiSnapshot.text(field,"package"))
                ||!UiSnapshot.bool(field,"editable")||!UiSnapshot.bool(field,"visible")
                ||UiSnapshot.bool(field,"password")
                ||!"城市/区县/商场等地点".equals(UiSnapshot.text(field,"hint")))return false;
        for(JsonObject n:ui.foreground().nodes)
            if("com.sankuai.meituan".equals(UiSnapshot.text(n,"package"))
                    &&"com.sankuai.meituan:id/msc_root".equals(UiSnapshot.text(n,"id")))return true;
        return false;
    }
    // Meituan's MSC location picker exports text/bounds but no click actions.
    // Permit only its public city navigation, never arbitrary text or saved addresses.
    private static JsonObject publicCityNavigation(UiSnapshot ui,int x,int y) {
        List<JsonObject> nodes=ui.foreground().nodes;
        int width=UiSnapshot.integer(ui.dump,"width"),height=UiSnapshot.integer(ui.dump,"height");
        if(width<=0||height<=0||x<0||y<0||x>=width||y>=height)return null;
        boolean msc=false,domestic=false,overseas=false,search=false,alphabet=false;
        for(JsonObject n:nodes) {
            if(!"com.sankuai.meituan".equals(UiSnapshot.text(n,"package")))continue;
            if("com.sankuai.meituan:id/msc_root".equals(UiSnapshot.text(n,"id")))msc=true;
            if(!UiSnapshot.bool(n,"visible"))continue;
            String text=UiSnapshot.text(n,"text");
            domestic|=text.equals("国内城市");overseas|=text.equals("海外地区");
            search|=text.equals("城市/区县/商场等地点");alphabet|=text.equals("城市开头字母");
        }
        if(!msc||!domestic||!overseas||!search)return null;
        for(JsonObject n:nodes) {
            if(!"com.sankuai.meituan".equals(UiSnapshot.text(n,"package"))
                    ||!UiSnapshot.bool(n,"visible")||!UiSnapshot.bool(n,"enabled")
                    ||UiSnapshot.bool(n,"password"))continue;
            String text=UiSnapshot.text(n,"text");
            if(!text.equals("国内城市")&&!text.equals("城市/区县/商场等地点")
                    &&!(alphabet&&text.matches("[A-Z]")))continue;
            int[] b=UiSnapshot.bounds(n);
            if(b[0]>=0&&b[1]>=0&&b[2]<=width&&b[3]<=height
                    &&b[2]>b[0]&&b[3]>b[1]&&x>=b[0]&&x<b[2]&&y>=b[1]&&y<b[3])return n;
        }
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
            if(selected==null&&"tap".equals(action))selected=publicCityNavigation(ui,a.x,a.y);
            return selected==null?"无法核实点击目标":forbidden(label(selected))?label(selected):"";
        }
        if(Arrays.asList("focus_input","text","clear_text","submit_search","key").contains(action)) {
            JsonObject field=input(ui,a.inputIndex,true);
            if(field==null||UiSnapshot.bool(field,"password")||(!search(label(field))&&!routeInput(ui,field)
                    &&!amapSearchInput(ui,field)&&!meituanLocationInput(ui,field)))return "非搜索或路线起终点输入框";
            if("key".equals(action)&&!Arrays.asList("enter","search").contains(a.key.toLowerCase(Locale.ROOT)))return "查询不允许该按键";
            return "";
        }
        return "查询不允许动作："+action;
    }
}
