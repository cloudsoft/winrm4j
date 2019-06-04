package io.cloudsoft.winrm4j.client.retry;

import java.util.Optional;
import java.util.function.Function;

/**
 * Policy to decide if a retry must be done.
 */
public interface RetryPolicy extends Function<Throwable, RetryDecision> {

    /**
     * @return total number of retries if known
     */
    default Optional<Integer> total() {
        return Optional.empty();
    }

    /**
     * Reset the state of the policy. This method MUST be called before enter the loop of retries.
     */
    default void clear() {
    }
}
