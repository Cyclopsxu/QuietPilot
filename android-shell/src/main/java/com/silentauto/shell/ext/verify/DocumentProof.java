package com.silentauto.shell.ext.verify;

import com.google.gson.JsonObject;
import com.silentauto.shell.ext.skill.UiSnapshot;

/** Exact, goal-bound Huawei memo proof. Draft text alone never proves persistence. */
public final class DocumentProof {
    private String title="",body="";
    private boolean observedExact,listed,reopened;
    private String state="not observed";
    public String state(){return state;}

    public void observe(String goal,String dump) {
        String[] expected=expected(goal);
        if(!title.equals(expected[0])||!body.equals(expected[1])) {
            title=expected[0];body=expected[1];observedExact=false;listed=false;reopened=false;
        }
        if(!supported())return;
        UiSnapshot snapshot=new UiSnapshot(dump).foreground();
        boolean titleExact=false,bodyPresent=false,bodyExact=false,hasList=false;
        for(JsonObject node:snapshot.nodes) {
            if(!"com.huawei.notepad".equals(UiSnapshot.text(node,"package"))||UiSnapshot.bool(node,"password")
                    ||(node.has("visible")&&!UiSnapshot.bool(node,"visible")))continue;
            String id=UiSnapshot.text(node,"id"),text=normalize(UiSnapshot.text(node,"text"));
            if(id.equals("com.huawei.notepad:id/title"))titleExact=title.equals(text);
            if(id.equals("com.huawei.notepad:id/notetext_textview")) {bodyPresent=true;bodyExact=body.equals(text);}
            if(id.equals("com.huawei.notepad:id/notes_listview"))hasList=true;
        }
        boolean exact=titleExact&&bodyExact;
        state="title="+titleExact+", bodyPresent="+bodyPresent+", body="+bodyExact+", list="+hasList;
        if(bodyPresent&&!exact){observedExact=false;listed=false;reopened=false;return;}
        // Huawei truncates list card titles. The subsequent exact editor title/body identify the note.
        if(observedExact&&!bodyPresent&&hasList)listed=true;
        // A later mutation or a different note invalidates a previous match immediately.
        reopened=listed&&exact;
        if(exact)observedExact=true;
    }
    public boolean supported(){return !title.isEmpty()&&!body.isEmpty();}
    public boolean verified(){return supported()&&reopened;}
    public String title(){return title;}
    public String bodyAnchor(){return body.substring(0,Math.min(90,body.length()));}
    public String missing(){return "请核对标题和完整正文（含末尾与链接），保存后返回列表，再打开同名笔记确认；仅草稿、摘要或字数不能证明已保存（"+state+", savedList="+listed+"）";}
    public static String normalize(String value){return value.replace("\r\n","\n").strip();}
    private static String[] expected(String goal) {
        String titlePrefix="\n标题：",start="\n完整正文开始：\n",end="\n完整正文结束。";
        int t=goal.indexOf(titlePrefix),b=goal.indexOf(start),e=goal.indexOf(end);
        if(t<0||b<=t||e<=b||goal.indexOf(start,b+start.length())>=0||goal.indexOf(end,e+end.length())>=0)return new String[]{"",""};
        int titleEnd=goal.indexOf('\n',t+titlePrefix.length());
        if(titleEnd<0||titleEnd>b)return new String[]{"",""};
        String title=normalize(goal.substring(t+titlePrefix.length(),titleEnd));
        String body=normalize(goal.substring(b+start.length(),e));
        return title.isEmpty()||body.isEmpty()?new String[]{"",""}:new String[]{title,body};
    }
}
