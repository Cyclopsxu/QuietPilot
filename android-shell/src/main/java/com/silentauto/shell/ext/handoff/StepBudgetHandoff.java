package com.silentauto.shell.ext.handoff;

/** Read-only final inspection, followed by an explicit, non-expiring user handoff. */
public final class StepBudgetHandoff {
    public interface Host {
        String inspect() throws Exception;
        void waiting(HandoffManager handoff, String evidence, String message);
        void applyAmendments();
    }

    public static String await(Host host) throws Exception {
        String evidence = "";
        String detail = "自动操作已达步数上限，当前页面已保留，请人工接管；尚未确认任务完成。";
        try {
            evidence = host.inspect();
            if (!evidence.isEmpty()) detail = "自动操作已达步数上限，已确认当前人工处理边界，页面已保留，请接管。";
        } catch (InterruptedException cancelled) {
            throw cancelled;
        } catch (Exception unavailable) {
            detail = "自动操作已达步数上限，收尾检查未能确认页面，现场仍已保留，请人工接管。";
        }
        while (true) {
            HandoffManager handoff = new HandoffManager();
            host.waiting(handoff, evidence, detail);
            // Never auto-resume, time out, or grant additional actions after exhaustion.
            String outcome = handoff.awaitAcknowledgement();
            if (!"amend".equals(outcome)) return outcome;
            host.applyAmendments();
            evidence = "";
            detail = "追加要求已收到，但自动操作步数已用尽，当前页面继续保留，请人工接管。";
        }
    }

    private StepBudgetHandoff() {}
}
