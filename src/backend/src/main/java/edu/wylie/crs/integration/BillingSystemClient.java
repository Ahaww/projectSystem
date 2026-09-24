package edu.wylie.crs.integration;

/**
 * 关闭注册后向计费系统发送事务。不可用时应按间隔重试直至恢复。
 */
public interface BillingSystemClient {

    void sendBilling(String studentId, String term, Object scheduleSnapshot);

    boolean isAvailable();
}
