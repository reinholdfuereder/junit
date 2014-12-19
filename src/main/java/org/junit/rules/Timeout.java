package org.junit.rules;

import java.util.concurrent.TimeUnit;

import org.junit.internal.runners.statements.FailOnTimeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * The Timeout Rule applies the same timeout to all test methods in a class:
 * <pre>
 * public static class HasGlobalLongTimeout {
 *
 *  &#064;Rule
 *  public Timeout globalTimeout= new Timeout(20);
 *
 *  &#064;Test
 *  public void run1() throws InterruptedException {
 *      Thread.sleep(100);
 *  }
 *
 *  &#064;Test
 *  public void infiniteLoop() {
 *      while (true) {}
 *  }
 * }
 * </pre>
 * <p>
 * Each test is run in a new thread. If the specified timeout elapses before
 * the test completes, its execution is interrupted via {@link Thread#interrupt()}.
 * This happens in interruptable I/O and locks, and methods in {@link Object}
 * and {@link Thread} throwing {@link InterruptedException}.
 * <p>
 * A specified timeout of 0 will be interpreted as not set, however tests will
 * still launch from separate threads. This can be useful for disabling timeouts
 * in environments where they are dynamically set based on some property.
 *
 * @since 4.7
 */
public class Timeout implements TestRule {

    /**
     * Optional system property with global timeout in milliseconds.
     */
    public static final String GLOBAL_TIMEOUT_MILLIS_PROPERTY_NAME = "org.junit.rules.timeout.global.millis";
    /**
     * Optional system property for global timeout strategy to use in case there was a local timeout set as well.
     */
    public static final String GLOBAL_TIMEOUT_STRATEGY_PROPERTY_NAME = "org.junit.rules.timeout.global.strategy";

    /**
     * Global timeout strategy in case there was a local timeout set as well.
     */
    public static enum GlobalTimeoutStrategy {
        /**
         * Should the global timeout conditionally override specific test timeouts that are higher then the global timeout: to
         * make sure a maximum test duration is not exceeded; also allows to test that something really must be very quick.
         */
        GLOBAL_TIMEOUT_OVERRIDES_HIGHER_LOCAL_TIMEOUT,
        /**
         * Should a specific timeout always override the global timeout: e.g. to avoid the need to increase the global timeout,
         * just because a single test takes longer.
         */
        GLOBAL_TIMEOUT_NEVER_OVERRIDES_LOCAL_TIMEOUT;
    }


    private final long timeout;
    private final TimeUnit timeUnit;
    private final boolean lookForStuckThread;

    /**
     * Returns a new builder for building an instance.
     *
     * @since 4.12
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a {@code Timeout} instance with the timeout specified
     * in milliseconds.
     * <p>
     * This constructor is deprecated.
     * <p>
     * Instead use {@link #Timeout(long, java.util.concurrent.TimeUnit)},
     * {@link Timeout#millis(long)}, or {@link Timeout#seconds(long)}.
     *
     * @param millis the maximum time in milliseconds to allow the
     * test to run before it should timeout
     */
    @Deprecated
    public Timeout(int millis) {
        this(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Create a {@code Timeout} instance with the timeout specified
     * at the timeUnit of granularity of the provided {@code TimeUnit}.
     *
     * @param timeout the maximum time to allow the test to run
     * before it should timeout
     * @param timeUnit the time unit for the {@code timeout}
     * @since 4.12
     */
    public Timeout(long timeout, TimeUnit timeUnit) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        lookForStuckThread = false;
    }

    /**
     * Create a {@code Timeout} instance initialized with values form
     * a builder.
     *
     * @since 4.12
     */
    protected Timeout(Builder builder) {
        timeout = builder.getTimeout();
        timeUnit = builder.getTimeUnit();
        lookForStuckThread = builder.getLookingForStuckThread();
    }

    /**
     * Creates a {@link Timeout} that will timeout a test after the
     * given duration, in milliseconds.
     *
     * @since 4.12
     */
    public static Timeout millis(long millis) {
        return new Timeout(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a {@link Timeout} that will timeout a test after the
     * given duration, in seconds.
     *
     * @since 4.12
     */
    public static Timeout seconds(long seconds) {
        return new Timeout(seconds, TimeUnit.SECONDS);
    }

    /**
     * Gets the timeout configured for this rule, in the given units.
     *
     * @since 4.12
     */
    protected final long getTimeout(TimeUnit unit) {
        return unit.convert(timeout, timeUnit);
    }

    /**
     * Gets whether this {@code Timeout} will look for a stuck thread
     * when the test times out.
     *
     * @since 4.12
     */
    protected final boolean getLookingForStuckThread() {
        return lookForStuckThread;
    }

    /**
     * Creates a {@link Statement} that will run the given
     * {@code statement}, and timeout the operation based
     * on the values configured in this rule. Subclasses
     * can override this method for different behavior.
     *
     * @since 4.12
     */
    protected Statement createFailOnTimeoutStatement(
            Statement statement) throws Exception {
        return FailOnTimeout.builder()
            .withTimeout(timeout, timeUnit)
            .withLookingForStuckThread(lookForStuckThread)
            .build(statement);
    }

    public Statement apply(Statement base, Description description) {
        try {
            return createFailOnTimeoutStatement(base);
        } catch (final Exception e) {
            return new Statement() {
                @Override public void evaluate() throws Throwable {
                    throw new RuntimeException("Invalid parameters for Timeout", e);
                }
            };
        }
    }

    /**
     * Builder for {@link Timeout}.
     *
     * @since 4.12
     */
    public static class Builder {
        private boolean lookForStuckThread = false;
        private long timeout = 0;
        private TimeUnit timeUnit = TimeUnit.SECONDS;

        protected Builder() {
        }

        /**
         * Specifies the time to wait before timing out the test.
         *
         * <p>If this is not called, or is called with a
         * {@code timeout} of {@code 0}, the returned {@code Timeout}
         * rule instance will cause the tests to wait forever to
         * complete, however the tests will still launch from a
         * separate thread. This can be useful for disabling timeouts
         * in environments where they are dynamically set based on
         * some property.
         *
         * @param timeout the maximum time to wait
         * @param unit the time unit of the {@code timeout} argument
         * @return {@code this} for method chaining.
         */
        public Builder withTimeout(long timeout, TimeUnit unit) {
            this.timeout = timeout;
            this.timeUnit = unit;
            return this;
        }

        protected long getTimeout() {
            return timeout;
        }

        protected TimeUnit getTimeUnit()  {
            return timeUnit;
        }

        /**
         * Specifies whether to look for a stuck thread.  If a timeout occurs and this
         * feature is enabled, the rule will look for a thread that appears to be stuck
         * and dump its backtrace.  This feature is experimental.  Behavior may change
         * after the 4.12 release in response to feedback.
         *
         * @param enable {@code true} to enable the feature
         * @return {@code this} for method chaining.
         */
        public Builder withLookingForStuckThread(boolean enable) {
            this.lookForStuckThread = enable;
            return this;
        }

        protected boolean getLookingForStuckThread() {
            return lookForStuckThread;
        }


        /**
         * Builds a {@link Timeout} instance using the values in this builder.,
         */
        public Timeout build() {
            return new Timeout(this);
        }
    }

    /**
     * Returns global timeout in milliseconds set via system property, or 0 if none is set.
     */
    public static long getGlobalTimeoutInMillis() {
        return Long.getLong(GLOBAL_TIMEOUT_MILLIS_PROPERTY_NAME, 0);
    }

    /**
     * Returns global timeout strategy set via system property, or 0 if none is set.
     */
    public static GlobalTimeoutStrategy getGlobalTimeoutStrategy() {
        String strategyStr = System.getProperty(GLOBAL_TIMEOUT_STRATEGY_PROPERTY_NAME, GlobalTimeoutStrategy.GLOBAL_TIMEOUT_OVERRIDES_HIGHER_LOCAL_TIMEOUT.toString());
        GlobalTimeoutStrategy strategy = GlobalTimeoutStrategy.valueOf(strategyStr);
        return strategy;
    }

    public static Timeout newTimeoutFromGlobalTimeout() {
        return getGlobalTimeoutInMillis() > 0 ? new Timeout(getGlobalTimeoutInMillis(), TimeUnit.MILLISECONDS) : null;
    }

    public Timeout newTimeoutFromGlobalTimeoutIfLessThanThisTimeout() {
        Timeout timeout = this;
        if (getGlobalTimeoutInMillis() > 0) {
            if (getGlobalTimeoutInMillis() < timeUnit.toMillis(this.timeout)) {
                timeout = builder().withTimeout(getGlobalTimeoutInMillis(), TimeUnit.MILLISECONDS)
                        .withLookingForStuckThread(lookForStuckThread).build();
            }
        }
        return timeout;
    }
}
