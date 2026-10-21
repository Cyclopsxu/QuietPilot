package com.silentauto.controller.ext.overlay;
import org.json.JSONObject;
import java.util.Collection;
/** Replaceable status presentation; native vendor surfaces can implement the same contract. */
public interface StatusSurface extends AutoCloseable {
    void update(Collection<JSONObject> tasks);
    void compact();
    @Override void close();
}
