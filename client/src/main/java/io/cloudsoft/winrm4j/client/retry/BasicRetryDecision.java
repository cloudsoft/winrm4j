package io.cloudsoft.winrm4j.client.retry;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

/**
 * Immutable Value Object to carry the decision made by the retry policy.
 * 
 * @since 0.8.0
 */
public class BasicRetryDecision implements RetryDecision {
    private final boolean shouldRetry;
    private final long pauseTimeMillis;
	private final Optional<String> reason;

    BasicRetryDecision(boolean shouldRetry, long pauseTimeMillis, Optional<String> reason) {
        this.shouldRetry = shouldRetry;
        this.pauseTimeMillis = pauseTimeMillis;
        this.reason = requireNonNull(reason, "reason");
    }

    @Override
    public boolean shouldRetry() {
        return shouldRetry;
    }

    @Override
    public long pauseTimeMillis() {
        return pauseTimeMillis;
    }

	@Override
	public Optional<String> reason() {
		return reason;
	}
}
