package com.silentauto.shell.ext.guard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ForegroundWatcher {
    private static final Pattern DISPLAY = Pattern.compile("^\\s*Display #([0-9]+).*");
    private static final Pattern RESUMED = Pattern.compile("(?:topResumedActivity|mResumedActivity|ResumedActivity).*?\\s([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)");

    public boolean appOnDisplay(int displayId,String pkg) throws Exception {
        Process process=new ProcessBuilder("dumpsys","activity","activities").redirectErrorStream(true).start();boolean selected=false,found=false;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){String line;
            while((line=reader.readLine())!=null){Matcher display=DISPLAY.matcher(line);if(display.matches())selected=String.valueOf(displayId).equals(display.group(1));if(selected&&line.contains(pkg+"/")&&(line.contains("Hist #")||line.contains("topResumedActivity")||line.contains("mResumedActivity")))found=true;}
        }finally{if(!process.waitFor(2,TimeUnit.SECONDS))process.destroy();}return found;
    }
    public String foregroundPackage() throws Exception { return foregroundActivity().split("/")[0]; }
    public String foregroundActivity() throws Exception {
        // ActivityManager exposes display sections on Android 10+, without occupying UiAutomation.
        Process process = new ProcessBuilder("dumpsys", "activity", "activities").redirectErrorStream(true).start();
        boolean main = false;
        String result = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher display = DISPLAY.matcher(line);
                if (display.matches()) main = "0".equals(display.group(1));
                if (!main) continue;
                Matcher resumed = RESUMED.matcher(line);
                if (resumed.find() && result.isEmpty()) result = resumed.group(1) + "/" + resumed.group(2);
            }
        } finally {
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy();
        }
        if (result.isEmpty()) throw new IllegalStateException("Unable to read main display foreground app");
        return result;
    }
}
