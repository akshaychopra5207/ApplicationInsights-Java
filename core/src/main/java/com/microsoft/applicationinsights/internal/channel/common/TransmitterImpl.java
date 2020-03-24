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

package com.microsoft.applicationinsights.internal.channel.common;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.applicationinsights.internal.channel.TelemetriesTransmitter;
import com.microsoft.applicationinsights.internal.channel.TelemetrySerializer;
import com.microsoft.applicationinsights.internal.channel.TransmissionDispatcher;
import com.microsoft.applicationinsights.internal.channel.TransmissionsLoader;
import com.microsoft.applicationinsights.internal.util.ThreadPoolUtils;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default implementation of the {@link TelemetriesTransmitter}
 *
 * The class is responsible holds the classes that do the actual sending to the server
 * Telemetry instances buffered in a collection are sent through this class.
 *
 * The class makes sure that the container of telemetries is sent using internal threads
 * and not the 'application' threads
 *
 * Created by gupele on 12/18/2014.
 */
public final class TransmitterImpl implements TelemetriesTransmitter<Telemetry> {

    private static final Logger logger = LoggerFactory.getLogger(TransmitterImpl.class);

    private static abstract class SendHandler {
        protected final TransmissionDispatcher transmissionDispatcher;

        protected final TelemetrySerializer serializer;

        protected SendHandler(TransmissionDispatcher transmissionDispatcher, TelemetrySerializer serializer) {
            Preconditions.checkNotNull(transmissionDispatcher, "transmissionDispatcher should be a non-null value");
            Preconditions.checkNotNull(serializer, "serializer should be a non-null value");

            this.transmissionDispatcher = transmissionDispatcher;
            this.serializer = serializer;
        }

        protected void dispatch(Collection<Telemetry> telemetries) {
            if (telemetries.isEmpty()) {
                return;
            }

            Optional<Transmission> transmission = serializer.serialize(telemetries);
            if (!transmission.isPresent()) {
                return;
            }

            transmissionDispatcher.dispatch(transmission.get());
        }
    }

    private static final class ScheduledSendHandler extends SendHandler implements Runnable {
        private final TelemetriesFetcher<Telemetry> telemetriesFetcher;

        public ScheduledSendHandler(TransmissionDispatcher transmissionDispatcher, TelemetriesFetcher<Telemetry> telemetriesFetcher, TelemetrySerializer serializer) {
            super(transmissionDispatcher,  serializer);

            Preconditions.checkNotNull(telemetriesFetcher, "telemetriesFetcher should be a non-null value");

            this.telemetriesFetcher = telemetriesFetcher;
        }

        @Override
        public void run() {
            Collection<Telemetry> telemetriesToSend = telemetriesFetcher.fetch();
            dispatch(telemetriesToSend);
        }
    }

    private static final class SendNowHandler extends SendHandler implements Runnable {
        private final Collection<Telemetry> telemetries;

        public SendNowHandler(TransmissionDispatcher transmissionDispatcher, TelemetrySerializer serializer, Collection<Telemetry> telemetries) {
            super(transmissionDispatcher,  serializer);

            Preconditions.checkNotNull(telemetries, "telemetries should be non-null value");

            this.telemetries = telemetries;
        }

        @Override
        public void run() {
            dispatch(telemetries);
        }
    }

    private static final int MAX_PENDING_SCHEDULE_REQUESTS = 16384;

    private static final AtomicInteger INSTANCE_ID_POOL = new AtomicInteger(1);

    private final TransmissionDispatcher transmissionDispatcher;

    private final TelemetrySerializer serializer;

    private final ScheduledExecutorService threadPool;

    private final TransmissionsLoader transmissionsLoader;

    private final Semaphore semaphore;

    private final int instanceId = INSTANCE_ID_POOL.getAndIncrement();

    public TransmitterImpl(TransmissionDispatcher transmissionDispatcher, TelemetrySerializer serializer, TransmissionsLoader transmissionsLoader) {
        Preconditions.checkNotNull(transmissionDispatcher, "transmissionDispatcher must be non-null value");
        Preconditions.checkNotNull(serializer, "serializer must be non-null value");
        Preconditions.checkNotNull(transmissionsLoader, "transmissionsLoader must be non-null value");

        this.transmissionDispatcher = transmissionDispatcher;
        this.serializer = serializer;

        semaphore = new Semaphore(MAX_PENDING_SCHEDULE_REQUESTS);

        threadPool = Executors.newScheduledThreadPool(2, ThreadPoolUtils.createDaemonThreadFactory(TransmitterImpl.class, instanceId));

        this.transmissionsLoader = transmissionsLoader;
        this.transmissionsLoader.load(false);
    }

    @Override
    public boolean scheduleSend(TelemetriesFetcher telemetriesFetcher, long value, TimeUnit timeUnit) {
        Preconditions.checkNotNull(telemetriesFetcher, "telemetriesFetcher should be non-null value");

        if (!semaphore.tryAcquire()) {
            return false;
        }

        try {
            final Runnable command = new ScheduledSendHandler(transmissionDispatcher, telemetriesFetcher, serializer);
            threadPool.schedule(new Runnable() {
                public void run() {
                    try {
                        semaphore.release();
                        command.run();
                    } catch (ThreadDeath td) {
                        throw td;
                    } catch (Throwable t) {
                        try {
                            logger.trace(t.getMessage(), t);
                        } catch (ThreadDeath td) {
                            throw td;
                        } catch (Throwable t2) {
                            // chomp
                        }
                    } finally {
                    }
                }
            }, value, timeUnit);

            return true;
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            try {
                semaphore.release();
                logger.error("Error in scheduledSend of telemetry items failed. {} items were not sent", telemetriesFetcher.fetch().size());
                logger.trace("Error in scheduledSend of telemetry items failed. {} items were not sent", telemetriesFetcher.fetch().size(), t);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
        }

        return true;
    }

    @Override
    public boolean sendNow(Collection<Telemetry> telemetries) {
        Preconditions.checkNotNull(telemetries, "telemetries should be non-null value");

        if (!semaphore.tryAcquire()) {
            return false;
        }

        final Runnable command = new SendNowHandler(transmissionDispatcher, serializer, telemetries);
        try {
            threadPool.execute(new Runnable() {
                public void run() {
                    try {
                        semaphore.release();
                        command.run();
                    } catch (ThreadDeath td) {
                        throw td;
                    } catch (Throwable t) {
                        try {
                            logger.error("exception in runnable sendNow()");
                            logger.trace("exception in runnable sendNow()", t);
                        } catch (ThreadDeath td) {
                            throw td;
                        } catch (Throwable t2) {
                            // chomp
                        }
                    } finally {
                    }
                }
            });

            return true;
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            try {
                semaphore.release();
                logger.error("Error in scheduledSend of telemetry items failed. {} items were not sent", telemetries.size());
                logger.trace("Error in scheduledSend of telemetry items failed. {} items were not sent", telemetries.size(), t);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
        }

        return false;
    }

    @Override
    public void stop(long timeout, TimeUnit timeUnit) {
        transmissionsLoader.stop(timeout, timeUnit);
        ThreadPoolUtils.stop(threadPool, timeout, timeUnit);
        transmissionDispatcher.stop(timeout, timeUnit);
    }
}
