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

import java.util.Collection;

import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.extensibility.TelemetryModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for performance modules.
 *
 * Created by gupele on 3/12/2015.
 */
public abstract class AbstractPerformanceCounterModule implements TelemetryModule {

    private static final Logger logger = LoggerFactory.getLogger(AbstractPerformanceCounterModule.class);

    protected final PerformanceCountersFactory factory;

    protected AbstractPerformanceCounterModule(PerformanceCountersFactory factory) {
        this.factory = factory;
    }

    /**
     * The main method will use the factory to fetch the performance counters and register them for work.
     * @param configuration The configuration to used to initialize the module.
     */
    @Override
    public void initialize(TelemetryConfiguration configuration) {
        Collection<PerformanceCounter> performanceCounters = factory.getPerformanceCounters();
        for (PerformanceCounter performanceCounter : performanceCounters) {
            try {
                PerformanceCounterContainer.INSTANCE.register(performanceCounter);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable e) {
                try {
                    logger.error("Failed to register performance counter '{}': '{}'", performanceCounter.getId(), e.toString());
                    logger.trace("Failed to register performance counter '{}'", performanceCounter.getId(), e);
                } catch (ThreadDeath td) {
                    throw td;
                } catch (Throwable t2) {
                    // chomp
                }
            }
        }
    }
}
