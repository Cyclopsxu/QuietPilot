package com.silentauto.shell.ext.query;
import com.google.gson.*;
public final class QueryTools {
    private QueryTools(){}
    public static JsonArray tools(JsonArray original){
        JsonArray result=new JsonArray();for(JsonElement value:original)if(!"finish".equals(value.getAsJsonObject().getAsJsonObject("function").get("name").getAsString()))result.add(value);
        result.add(tool("report_answer","Report an answer of at most 60 characters and the exact visible quote supporting it",new String[]{"answer","quote"}));
        result.add(tool("report_not_found","Report where you searched when the information cannot be found",new String[]{"searched"}));return result;
    }
    private static JsonObject tool(String name,String description,String[] fields){
        JsonObject root=new JsonObject(),function=new JsonObject(),parameters=new JsonObject(),properties=new JsonObject();JsonArray required=new JsonArray();
        root.addProperty("type","function");function.addProperty("name",name);function.addProperty("description",description);
        parameters.addProperty("type","object");for(String field:fields){JsonObject value=new JsonObject();value.addProperty("type","string");properties.add(field,value);required.add(field);}
        parameters.add("properties",properties);parameters.add("required",required);function.add("parameters",parameters);root.add("function",function);return root;
    }
}
