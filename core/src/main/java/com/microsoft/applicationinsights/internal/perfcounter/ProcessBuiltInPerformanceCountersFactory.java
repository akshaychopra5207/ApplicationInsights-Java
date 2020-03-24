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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.microsoft.applicationinsights.internal.system.SystemInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class will create the 'built-in'/default performance counters.
 *
 * Created by gupele on 3/3/2015.
 */
final class ProcessBuiltInPerformanceCountersFactory implements PerformanceCountersFactory, WindowsPerformanceCountersFactory {

    private static final Logger logger = LoggerFactory.getLogger(ProcessBuiltInPerformanceCountersFactory.class);

    private Iterable<WindowsPerformanceCounterData> windowsPCsData;

    /**
     * Creates the {@link com.microsoft.applicationinsights.internal.perfcounter.PerformanceCounter} that are
     * the 'built-in' performance counters of the process.
     *
     * Note: The method should not throw
     *
     * @return A collection of {@link com.microsoft.applicationinsights.internal.perfcounter.PerformanceCounter}
     */
    public Collection<PerformanceCounter> getPerformanceCounters() {
        try {
            if (SystemInformation.INSTANCE.isWindows()) {
                return getWindowsPerformanceCounters();
            } else if (SystemInformation.INSTANCE.isUnix()) {
                return getUnixPerformanceCounters();
            } else {
                logger.error("Unknown OS, performance counters are not created.");
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            try {
                logger.error("Error while creating performance counters: '{}'", t.toString());            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
        }

        return Collections.emptyList();
    }

    /**
     * Creates the performance counters that are suitable for all supported OSs.
     * @return An array of generic performance counters.
     */
    private ArrayList<PerformanceCounter> getMutualPerformanceCounters() {
        ArrayList<PerformanceCounter> performanceCounters = new ArrayList<PerformanceCounter>();

        performanceCounters.add(new ProcessCpuPerformanceCounter());
        performanceCounters.add(new ProcessMemoryPerformanceCounter());
        performanceCounters.add(new FreeMemoryPerformanceCounter());

        return performanceCounters;
    }

    /**
     * Returns the performance counters that are useful for Unix OS.
     * @return Collection of Windows performance counters.
     */
    private Collection<PerformanceCounter> getUnixPerformanceCounters() {
        ArrayList<PerformanceCounter> performanceCounters = getMutualPerformanceCounters();
        performanceCounters.add(new UnixProcessIOPerformanceCounter());
        performanceCounters.add(new UnixTotalCpuPerformanceCounter());

        return performanceCounters;
    }

    /**
     * Returns the performance counters that are useful for Windows OSs.
     * @return Collection of Unix performance counters.
     */
    private Collection<PerformanceCounter> getWindowsPerformanceCounters() {
        ArrayList<PerformanceCounter> performanceCounters = getMutualPerformanceCounters();

        try {
            if (windowsPCsData != null) {
                WindowsPerformanceCounterAsMetric pcWindowsMetric = new WindowsPerformanceCounterAsMetric(windowsPCsData);
                performanceCounters.add(pcWindowsMetric);
                windowsPCsData = null;
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable e) {
            try {
                logger.error("Failed to create WindowsPerformanceCounterAsMetric: '{}'", e.toString());
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
        }

        try {
            WindowsPerformanceCounterAsPC pcWindowsPCs = new WindowsPerformanceCounterAsPC();
            performanceCounters.add(pcWindowsPCs);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable e) {
            try {
                logger.error("Failed to create WindowsPerformanceCounterAsPC: '{}'", e.toString());
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
        }

        return performanceCounters;
    }

    @Override
    public void setWindowsPCs(Iterable<WindowsPerformanceCounterData> windowsPCsData) {
        this.windowsPCsData = windowsPCsData;
    }
}
