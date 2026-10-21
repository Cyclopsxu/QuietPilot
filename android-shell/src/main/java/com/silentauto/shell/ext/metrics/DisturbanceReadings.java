package com.silentauto.shell.ext.metrics;

import java.util.regex.*;

/** Parsers accept platform differences; missing values stay unknown, never fabricated zeros. */
public final class DisturbanceReadings {
    private static final Pattern DISPLAY=Pattern.compile("Display: mDisplayId=(\\d+)");
    private static final Pattern PACKAGE=Pattern.compile("u\\d+ ([A-Za-z0-9_.]+)/");
    private static final Pattern MAIN_TOUCH=Pattern.compile("displayId=0[,)]");
    private static final Pattern AGE=Pattern.compile("age=([0-9.]+)ms");
    public String foreground="";
    public int focusedDisplay=-1,imeDisplay=-1;
    public long touchAgeMs=-1;
    public boolean touchHistoryAvailable;
    public static DisturbanceReadings parse(String windows,String input,String ime){
        DisturbanceReadings result=new DisturbanceReadings();int display=-1;
        for(String line:windows.split("\n")){
            Matcher section=DISPLAY.matcher(line);if(section.find())display=Integer.parseInt(section.group(1));
            if(display==0&&line.contains("mFocusedApp=")){Matcher pkg=PACKAGE.matcher(line);if(pkg.find())result.foreground=pkg.group(1);}
        }
        result.focusedDisplay=number(windows,"mTopFocusedDisplay(?:Id)?[=:]\\s*(\\d+)");
        result.imeDisplay=number(ime,"mDisplayIdToShowIme[=:]\\s*(\\d+)");
        if(result.imeDisplay<0)result.imeDisplay=number(ime,"mCurTokenDisplayId[=:]\\s*(\\d+)");
        result.touchHistoryAvailable=input.contains("RecentQueue:");
        for(String line:input.split("\n"))if(line.contains("MotionEvent")&&(line.contains("source=TOUCHSCREEN")||line.contains("source=0x1002"))&&MAIN_TOUCH.matcher(line).find()){
            Matcher age=AGE.matcher(line);if(age.find()){long value=(long)Double.parseDouble(age.group(1));if(result.touchAgeMs<0||value<result.touchAgeMs)result.touchAgeMs=value;}
        }
        return result;
    }
    private static int number(String text,String pattern){Matcher matcher=Pattern.compile(pattern).matcher(text);return matcher.find()?Integer.parseInt(matcher.group(1)):-1;}
    public static long[] frames(String text){Matcher total=Pattern.compile("(?m)^\\s*Total frames rendered:\\s*(\\d+)").matcher(text);Matcher janky=Pattern.compile("(?m)^\\s*Janky frames:\\s*(\\d+)").matcher(text);return total.find()&&janky.find()?new long[]{Long.parseLong(total.group(1)),Long.parseLong(janky.group(1))}:null;}
}
