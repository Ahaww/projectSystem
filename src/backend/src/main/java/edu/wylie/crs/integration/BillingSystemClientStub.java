package edu.wylie.crs.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 学院计费系统 Stub。成功只打日志；测试可关闭可用性以验证重试队列。
 */
@Component
public class BillingSystemClientStub implements BillingSystemClient {

    private static final Logger log = LoggerFactory.getLogger(BillingSystemClientStub.class);

    private volatile boolean available = true;
    private volatile boolean failNext = false;
    private final AtomicInteger sendCount = new AtomicInteger();

    @Override
    public void sendBilling(String studentId, String term, Object scheduleSnapshot) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("计费系统投递失败");
        }
        sendCount.incrementAndGet();
        log.info("计费 Stub 已接收事务：studentId={} term={} snapshot={}", studentId, term, scheduleSnapshot);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /** 测试用：模拟计费系统不可达 */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    /** 测试用：下一次 sendBilling 抛错（即使 isAvailable=true） */
    public void setFailNext(boolean failNext) {
        this.failNext = failNext;
    }

    public int getSendCount() {
        return sendCount.get();
    }

    public void resetSendCount() {
        sendCount.set(0);
    }
}
