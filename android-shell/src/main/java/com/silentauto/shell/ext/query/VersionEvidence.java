package com.silentauto.shell.ext.query;
import java.util.Locale;

/** Reject adjacent kernel/build-host fields when the question asks for the phone OS version. */
public final class VersionEvidence {
    private VersionEvidence(){}
    public static void validate(String question,String answer,String quote) {
        String q=question.toLowerCase(Locale.ROOT);
        if((q.contains("内核")||q.contains("kernel"))&&!q.contains("系统版本")&&!q.contains("软件版本"))return;
        boolean version=q.contains("版本")||q.contains("version");
        boolean system=q.contains("harmonyos")||q.contains("鸿蒙")||q.contains("android")||q.contains("安卓")||q.contains("ios")||q.contains("系统版本");
        if(!version||!system)return;
        String evidence=(answer+"\n"+quote).toLowerCase(Locale.ROOT);
        if(evidence.matches("(?s).*(\\blocalhost\\b|\\bkernel\\b|\\blinux\\b|内核|编译主机).*"))
            throw new IllegalArgumentException("当前依据是内核/编译信息，不是所问系统版本；请更换入口读取软件版本");
        String value=AnswerVerifier.normalize(answer).replaceFirst("(?i)^(?:harmonyos|鸿蒙)?(?:版本号?|系统版本号?)?[:：]?","");
        if(q.contains("完整")&&q.contains("构建号")&&value.matches("\\d+(?:\\.\\d+){0,2}"))
            throw new IllegalArgumentException("只有主版本，缺少问题要求的完整构建号；请继续查找软件版本");
    }
}
