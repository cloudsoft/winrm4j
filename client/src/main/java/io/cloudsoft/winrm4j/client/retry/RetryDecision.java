package io.cloudsoft.winrm4j.client.retry;

/**
 * Decision about apply a retry after an exception
 */
public interface RetryDecision {
    /**
     * @return {@code true} if a new retry must be done
     */
    boolean retry();

    /**
     * @return duration in milliseconds of the sleep before the next retry
     */
    long pause();
}