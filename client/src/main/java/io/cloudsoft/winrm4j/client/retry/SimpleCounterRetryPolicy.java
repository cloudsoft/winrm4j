package io.cloudsoft.winrm4j.client.retry;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Basic implementation of a retry policy based on a simple counter. The duration of the pause is static.
 */
public class SimpleCounterRetryPolicy implements RetryPolicy {
    private final long pause;
    private final int total;
    private int counter;

    /**
     * @param total         total number of retries
     * @param pause         duration of the sleep between each retries
     * @param pauseUnit     unit of the duration
     */
    public SimpleCounterRetryPolicy(int total, long pause, TimeUnit pauseUnit) {
        this.total = total;
        this.pause = pauseUnit.toMillis(pause);
    }

    @Override
    public Optional<Integer> total() {
        return Optional.of(total);
    }

    @Override
    public void clear() {
        counter = 0;
    }

    @Override
    public RetryDecision apply(Throwable t) {
        counter++;
        return new DefaultRetryDecision(counter <= total, pause);
    }
}
