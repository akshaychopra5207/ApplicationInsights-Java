/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.internal.perfcounter;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.internal.shutdown.Stoppable;
import com.microsoft.applicationinsights.internal.util.ThreadPoolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class serves as the container of all {@link com.microsoft.applicationinsights.internal.perfcounter.PerformanceCounter}
 * <p>
 * If there is a need for a performance counter, the user of this class should create an implementation of that interface
 * and then register it in this container.
 * <p>
 * Note that the container will only start working after the first registration of a Performance Counter.
 * That means that setting the timeouts is only relevant if done before the first registration of a Performance Counter.
 * <p>
 * The container will go through all the registered Performance Counters and will trigger their 'report' method.
 * By default the container will start reporting after 5 minutes and will continue doing so every 1 minute.
 * <p>
 * The user of this class can add (register), remove (unregister) a performance counter while the container is working.
 * <p>
 * The container will be stopped automatically when the application exists.
 * <p>
 * Created by gupele on 3/3/2015.
 */
public enum PerformanceCounterContainer implements Stoppable {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(PerformanceCounterContainer.class);

    // By default the container will wait 2 minutes before the collection of performance data.
    private final static long START_COLLECTING_DELAY_IN_MILLIS = 60000;
    private final static long START_DEFAULT_MIN_DELAY_IN_MILLIS = 20000;

    // By default the container will collect performance data every 1 minute.
    public final static long DEFAULT_COLLECTION_FREQUENCY_IN_SEC = 60;
    private final static long MIN_COLLECTION_FREQUENCY_IN_SEC = 1;

    private final ConcurrentMap<String, PerformanceCounter> performanceCounters = new ConcurrentHashMap<String, PerformanceCounter>();

    private volatile boolean initialized = false;

    private long startCollectingDelayInMillis = START_COLLECTING_DELAY_IN_MILLIS;
    private long collectionFrequencyInMS = DEFAULT_COLLECTION_FREQUENCY_IN_SEC * 1000;

    private TelemetryClient telemetryClient;

    private ScheduledThreadPoolExecutor threads;

    /**
     * /**
     * Registers a {@link com.microsoft.applicationinsights.internal.perfcounter.PerformanceCounter} that can collect data.
     *
     * @param performanceCounter The Performance Counter.
     * @return True on success.
     */
    public boolean register(PerformanceCounter performanceCounter) {
        Preconditions.checkNotNull(performanceCounter, "performanceCounter should be non null, non empty value");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(performanceCounter.getId()), "performanceCounter's id should be non null, non empty value");

        initialize();

        logger.trace("Registering PC '{}'", performanceCounter.getId());
        PerformanceCounter prev = performanceCounters.putIfAbsent(performanceCounter.getId(), performanceCounter);
        if (prev != null) {
            logger.trace("Failed to store performance counter '{}', since there is already one", performanceCounter.getId());
            return false;
        }

        return true;
    }

    /**
     * Un-registers a performance counter.
     *
     * @param performanceCounter The Performance Counter.
     */
    public void unregister(PerformanceCounter performanceCounter) {
        unregister(performanceCounter.getId());
    }

    /**
     * Un-registers a performance counter by its id.
     *
     * @param id The Performance Counter's id.
     */
    public void unregister(String id) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(id), "id should be non null, non empty value");

        logger.trace("Un-registering PC '{}'", id);
        performanceCounters.remove(id);
    }

    /**
     * Gets the timeout in milliseconds that the container will wait before the first collection of Performance Counters.
     *
     * @return The first timeout in milliseconds.
     */
    public long getStartCollectingDelayInMillis() {
        return startCollectingDelayInMillis;
    }

    /**
     * Gets the timeout in milliseconds that the container will wait between collections of Performance Counters.
     *
     * @return The timeout between collections.
     */
    public long getCollectionFrequencyInSec() {
        return collectionFrequencyInMS / 1000;
    }

    /**
     * Stopping the collection of performance data.
     *
     * @param timeout  The timeout to wait for the stop to happen.
     * @param timeUnit The time unit to use when waiting for the stop to happen.
     */
    public synchronized void stop(long timeout, TimeUnit timeUnit) {
        if (!initialized) {
            return;
        }

        ThreadPoolUtils.stop(threads, timeout, timeUnit);
        initialized = false;
    }

    /**
     * Sets the timeout to wait between collection of Performance Counters.
     * <p>
     * The number must be a positive number
     * <p>
     * Note that the method will be effective if called before the first call to the 'register' method.
     *
     * @param collectionFrequencyInSec The timeout to wait between collection of Performance Counters.
     */
    public void setCollectionFrequencyInSec(long collectionFrequencyInSec) {
        if (collectionFrequencyInSec <= MIN_COLLECTION_FREQUENCY_IN_SEC) {
            String errorMessage = String.format("Collecting Interval: illegal value '%d'. The minimum value, '%d', " +
                    "is used instead.", collectionFrequencyInSec, MIN_COLLECTION_FREQUENCY_IN_SEC);
            logger.error(errorMessage);

            collectionFrequencyInSec = MIN_COLLECTION_FREQUENCY_IN_SEC;
        }

        this.collectionFrequencyInMS = collectionFrequencyInSec * 1000;
    }

    /**
     * Sets the timeout to wait before the first reporting.
     * <p>
     * The number must be a positive number
     * <p>
     * Note that the method will be effective if called before the first call to the 'register' method.
     *
     * @param startCollectingDelayInMillis Timeout to wait before the first collection of performance counters in milliseconds.
     */
    void setStartCollectingDelayInMillis(long startCollectingDelayInMillis) {
        if (startCollectingDelayInMillis < START_DEFAULT_MIN_DELAY_IN_MILLIS) {
            logger.error("Start Collecting Delay: illegal value '%d'. The minimum value, '%'d, is used instead.", startCollectingDelayInMillis, START_DEFAULT_MIN_DELAY_IN_MILLIS);

            startCollectingDelayInMillis = START_DEFAULT_MIN_DELAY_IN_MILLIS;
        }

        this.startCollectingDelayInMillis = startCollectingDelayInMillis;
    }

    void clear() {
        performanceCounters.clear();
    }

    /**
     * A private method that is called only when the container needs to start
     * collecting performance counters data. The method will schedule a callback
     * to be called, it will initialize a {@link com.microsoft.applicationinsights.TelemetryClient} that the Performance Counters
     * will use to report their data
     */
    private void initialize() {
        if (!initialized) {
            synchronized (INSTANCE) {
                if (!initialized) {
                    createThreadToCollect();

                    scheduleWork();

                    initialized = true;
                }
            }
        }
    }

    private void scheduleWork() {
        threads.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        if (telemetryClient == null) {
                            telemetryClient = new TelemetryClient();
                        }

                        for (PerformanceCounter performanceCounter : performanceCounters.values()) {
                            try {
                                performanceCounter.report(telemetryClient);
                            } catch (ThreadDeath td) {
                                throw td;
                            } catch (Throwable t) {
                                try {
                                    logger.error("Exception while reporting performance counter '{}'", performanceCounter.getId(), t);
                                } catch (ThreadDeath td) {
                                    throw td;
                                } catch (Throwable t2) {
                                    // chomp
                                }
                            }
                        }
                    }
                },
                startCollectingDelayInMillis,
                collectionFrequencyInMS,
                TimeUnit.MILLISECONDS);
    }

    private void createThreadToCollect() {
        threads = new ScheduledThreadPoolExecutor(1);
        threads.setThreadFactory(ThreadPoolUtils.createDaemonThreadFactory(PerformanceCounterContainer.class));
    }
}
