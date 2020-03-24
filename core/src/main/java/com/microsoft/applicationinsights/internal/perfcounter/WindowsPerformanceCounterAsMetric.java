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

import java.util.HashMap;
import java.util.Map;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.internal.system.SystemInformation;
import com.microsoft.applicationinsights.telemetry.MetricTelemetry;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance counters that are sent as {@link com.microsoft.applicationinsights.telemetry.MetricTelemetry}
 *
 * Created by gupele on 3/30/2015.
 */
public final class WindowsPerformanceCounterAsMetric extends AbstractWindowsPerformanceCounter {

    private static final Logger logger = LoggerFactory.getLogger(WindowsPerformanceCounterAsMetric.class);

    private static final String ID = Constants.PERFORMANCE_COUNTER_PREFIX + "WindowsPerformanceCounterAsMetric";

    private final HashMap<String, String> keyToDisplayName = new HashMap<String, String>();
    private final MetricTelemetry telemetry = new MetricTelemetry();

    /**
     * Registers the argument's data into performance counters.
     * @param pcsData The performance counters to register for Windows OS.
     * @throws java.lang.Throwable The constructor might throw an Error if the JniPCConnector is not able to properly
     * connect to the native code. or Exception if the constructor is not called under Windows OS.
     */
    public WindowsPerformanceCounterAsMetric(Iterable<WindowsPerformanceCounterData> pcsData) throws Throwable {
        if (!SystemInformation.INSTANCE.isWindows()) {
            throw new IllegalStateException("Must be used under Windows OS.");
        }
        if (pcsData == null) {
            throw new NullPointerException("pcsData must be non-null value.");
        }

        // indicate that this is used for performance counters, not custom metrics.
        telemetry.markAsCustomPerfCounter();

        register(pcsData);
        if (keyToDisplayName.isEmpty()) {
            // Failed to register, the performance counter is not needed.
            throw new Exception("No valid data");
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    /**
     * Go through the data that we have and ask for each key its value, and then send using the requested display name.
     * The method might throw an Error if the JniPCConnector is not able to properly connect to the native code.
     * @param telemetryClient The {@link com.microsoft.applicationinsights.TelemetryClient} to use for sending
     */
    @Override
    public void report(TelemetryClient telemetryClient) {
        for (Map.Entry<String, String> entry : keyToDisplayName.entrySet()) {
            try {
                double value = JniPCConnector.getValueOfPerformanceCounter(entry.getKey());
                if (value < 0) {
                    reportError(value, entry.getValue());
                } else {
                    send(telemetryClient, value, entry.getValue());
                    logger.trace("Sent metric performance counter for '{}': '{}'", entry.getValue(), value);
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable e) {
                try {
                    logger.error("Failed to send metric performance counter for '{}': '{}'", entry.getValue(), e.toString());
                    logger.trace("Failed to send metric performance counter for '{}'", entry.getValue(), e);
                } catch (ThreadDeath td) {
                    throw td;
                } catch (Throwable t2) {
                    // chomp
                }
            }
        }
    }

    /**
     * Register the requested performance counters using the native code, and storing the display name for future sending of data.
     * @param pcsData The requested performance counters.
     */
    private void register(Iterable<WindowsPerformanceCounterData> pcsData) {
        for (WindowsPerformanceCounterData data : pcsData) {
            try {
                String key = JniPCConnector.addPerformanceCounter(data.getCategoryName(), data.getCounterName(), data.getInstanceName());
                if (!StringUtils.isEmpty(key)) {
                    keyToDisplayName.put(key, data.getDisplayName());
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t) {
                try {
                    logger.trace("error registering {}", data.getDisplayName(), t);
                } catch (ThreadDeath td) {
                    throw td;
                } catch (Throwable t2) {
                    // chomp
                }
            }
        }
    }

    protected void send(TelemetryClient telemetryClient, double value, String key) {
        // Using the metric to avoid unneeded allocations
        telemetry.setName(key);
        telemetry.setValue(value);
        telemetryClient.track(telemetry);
    }
}
