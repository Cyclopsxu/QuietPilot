package com.silentauto.shell.ext.query;

public enum TaskType {
    ACTION, QUERY;
    public static TaskType parse(String value) {
        if(value==null||value.isEmpty())return ACTION;
        try{return valueOf(value.toUpperCase(java.util.Locale.ROOT));}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("type must be action or query");}
    }
    public String wire(){return name().toLowerCase(java.util.Locale.ROOT);}
}
