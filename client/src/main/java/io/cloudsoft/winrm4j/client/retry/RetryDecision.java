package io.cloudsoft.winrm4j.client.retry;

import java.util.Optional;

/**
 * Decision about whether to retry.
 * 
 * @see RetryPolicy
 * 
 * @since 0.8.0
 */
public interface RetryDecision {
    /**
     * @return {@code true} if a new retry should be done
     */
    boolean shouldRetry();

    /**
     * @return duration in milliseconds of the sleep before the next retry
     */
    long pauseTimeMillis();
    
    /**
     * @return the reason to retry or to fail (e.g. "attempt 1 of 3")
     */
    Optional<String> reason();
}