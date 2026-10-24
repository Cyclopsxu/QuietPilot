package com.silentauto.shell.ext.guard;

/** A cached accessibility node may not mutate a different display or task package. */
public final class NodeWritePolicy {
    private NodeWritePolicy() {}
    public static boolean permits(int requestedDisplay,int actualDisplay,String actualPackage,String expectedPackage) {
        return requestedDisplay>=0&&actualDisplay==requestedDisplay
                &&(expectedPackage==null||expectedPackage.isEmpty()||expectedPackage.equals(actualPackage));
    }
}
