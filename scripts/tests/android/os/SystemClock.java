package android.os;

/** Host-only monotonic clock for TaskProgress regression; never packaged in Android builds. */
public final class SystemClock {
    public static long elapsedRealtime(){return System.nanoTime()/1_000_000;}
}
