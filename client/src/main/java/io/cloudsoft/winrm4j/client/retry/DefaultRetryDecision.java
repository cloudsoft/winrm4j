package io.cloudsoft.winrm4j.client.retry;

/**
 * Immutable Value Object to carry the decision make by the retry policy.
 */
public class DefaultRetryDecision implements RetryDecision {
    private final boolean retry;
    private final long pause;

    DefaultRetryDecision(boolean retry, long pause) {
        this.retry = retry;
        this.pause = pause;
    }

    @Override
    public boolean retry() {
        return retry;
    }

    @Override
    public long pause() {
        return pause;
    }
}
