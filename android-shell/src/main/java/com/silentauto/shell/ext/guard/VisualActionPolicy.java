package com.silentauto.shell.ext.guard;

import com.silentauto.shell.ActionDecision;
import com.google.gson.*;

public final class VisualActionPolicy {
    private VisualActionPolicy(){}
    public static void validateMessageBinding(String goal,String message,String recipient){
        if(message.isEmpty()&&recipient.isEmpty())return;
        if(message.isEmpty()||recipient.isEmpty()||!goal.contains(message)||!goal.contains(recipient))
            throw new IllegalArgumentException("Exact message and recipient must both occur verbatim in the authorized goal");
        if(messageId(message)==null)throw new IllegalArgumentException("Exact visual message requires one unique numeric marker of at least 10 digits");
    }
    public static void validateMessageVersion(boolean sent,long sentVersion,long goalVersion){
        if(sent&&sentVersion!=goalVersion)throw new IllegalStateException("Goal changed after visual send; the previous message cannot verify the amended goal or be replayed");
    }
    private static String messageId(String expected){
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("\\d{10,}").matcher(expected);
        if(!matcher.find())return null;String id=matcher.group();return matcher.find()?null:id;
    }
    public static void ground(ActionDecision action,JsonObject raw,JsonArray results,int width,int height){
        if(!java.util.Set.of("tap","text").contains(action.action))return;
        String label=raw.has("target_text")?raw.get("target_text").getAsString().trim():"";
        if(!label.isEmpty()) {
            JsonObject found=null;
            for(JsonElement e:results){JsonObject o=e.getAsJsonObject();
                if(!o.has("text")||!label.equals(o.get("text").getAsString().trim())||o.get("confidence").getAsDouble()<.85)continue;
                if(found!=null)throw new IllegalArgumentException("Ambiguous OCR target: "+label);found=o;
            }
            if(found==null)throw new IllegalArgumentException("OCR target not currently visible: "+label);
            JsonObject b=found.getAsJsonObject("bounds");action.x=b.get("centerX").getAsInt();action.y=b.get("centerY").getAsInt();return;
        }
        if(!raw.has("x")||!raw.has("y")||!raw.has("coordinate_space")||!"normalized_1000".equals(raw.get("coordinate_space").getAsString()))
            throw new IllegalArgumentException("Visual coordinates must declare normalized_1000");
        if(action.x<0||action.x>1000||action.y<0||action.y>1000)throw new IllegalArgumentException("Invalid normalized visual coordinates");
        action.x=Math.min(width-1,(int)Math.round(action.x*width/1000.0));
        action.y=Math.min(height-1,(int)Math.round(action.y*height/1000.0));
    }
    public static void validate(ActionDecision a,int width,int height){
        if(!java.util.Set.of("tap","text","scroll","wait","done","request_user","report_answer","report_not_found").contains(a.action))
            throw new IllegalArgumentException("Unsupported visual action: "+a.action);
        if(java.util.Set.of("tap","text").contains(a.action)&&(a.x<0||a.y<0||a.x>=width||a.y>=height))
            throw new IllegalArgumentException("Visual coordinates outside task display");
        if("text".equals(a.action)&&a.text.isEmpty())throw new IllegalArgumentException("Empty visual paste");
    }
    /** OCR may merge adjacent menu labels; click only the leading paste label. */
    public static int[] pasteTarget(JsonArray results,int width,int height){
        return menuTarget(results,"粘贴",width,height);
    }
    public static int[] menuTarget(JsonArray results,String label,int width,int height){
        int[] found=null;
        for(JsonElement e:results){
            JsonObject o=e.getAsJsonObject();String text=o.has("text")?o.get("text").getAsString().trim():"";
            if(!text.startsWith(label)||!o.has("confidence")||o.get("confidence").getAsDouble()<.85)continue;
            JsonObject b=o.getAsJsonObject("bounds");if(b==null)continue;
            int l=b.get("left").getAsInt(),t=b.get("top").getAsInt(),r=b.get("right").getAsInt(),bottom=b.get("bottom").getAsInt();
            if(l<0||t<0||r<=l||bottom<=t||r>width||bottom>height)continue;
            if(found!=null)return null;
            found=new int[]{l+(r-l)/Math.max(2,text.codePointCount(0,text.length())),(t+bottom)/2};
        }
        return found;
    }
    public static boolean hasTitle(JsonArray rows,String title,int height){
        if(title.isEmpty())return false;
        for(JsonElement e:rows){JsonObject o=e.getAsJsonObject();
            if(title.equals(o.get("text").getAsString().trim())&&o.get("confidence").getAsDouble()>=.9
                    &&o.getAsJsonObject("bounds").get("bottom").getAsInt()<height*.15)return true;
        }return false;
    }
    /** For numbered messages, fail closed unless the sole OCR marker is in the bottom draft area. */
    public static boolean uniqueDraftMarker(JsonArray rows,String expected,int height){
        String marker=messageId(expected);if(marker==null)return false;
        int count=0;
        for(JsonElement e:rows){JsonObject row=e.getAsJsonObject();
            if(!row.get("text").getAsString().contains(marker))continue;
            if(row.get("confidence").getAsDouble()<.9||row.getAsJsonObject("bounds").get("top").getAsInt()<height*.85)return false;
            count++;
        }
        return count==1;
    }

    public static int[] messageMarker(JsonArray rows,String expected){
        String id=messageId(expected);if(id==null)return null;
        int[] target=null;
        for(JsonElement e:rows){JsonObject row=e.getAsJsonObject();
            if(!row.has("text")||!row.get("text").getAsString().contains(id)||row.get("confidence").getAsDouble()<.85)continue;
            if(target!=null)return null;JsonObject b=row.getAsJsonObject("bounds");target=new int[]{b.get("centerX").getAsInt(),b.get("centerY").getAsInt()};
        }return target;
    }
    public static boolean sendTap(ActionDecision a,JsonArray results){
        if(!"tap".equals(a.action))return false;
        for(JsonElement e:results){JsonObject o=e.getAsJsonObject();
            if(!o.has("text")||!"发送".equals(o.get("text").getAsString().trim())||!o.has("bounds"))continue;
            JsonObject b=o.getAsJsonObject("bounds");
            if(a.x>=b.get("left").getAsInt()&&a.x<=b.get("right").getAsInt()&&a.y>=b.get("top").getAsInt()&&a.y<=b.get("bottom").getAsInt())return true;
        }return false;
    }
}
