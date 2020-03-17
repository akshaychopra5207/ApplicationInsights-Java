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

package com.microsoft.applicationinsights.internal.channel.stdout;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Charsets;
import com.microsoft.applicationinsights.channel.TelemetryChannel;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.telemetry.JsonTelemetryDataSerializer;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.squareup.moshi.JsonWriter;
import okio.Buffer;

/**
 * A telemetry channel routing information to stdout.
 */
public class StdOutChannel implements TelemetryChannel
{
    @Override
    public boolean isDeveloperMode() {
        return false;
    }

    @Override
    public void setDeveloperMode(boolean value) {
        // Just ignore it.
    }

    public StdOutChannel() {
        this(null);
    }

    public StdOutChannel(Map<String, String> namesAndValues) {
    }

    @Override
    public void send(Telemetry item) {
        try {
            Buffer buffer = new Buffer();
            JsonWriter writer = JsonWriter.of(buffer);
            JsonTelemetryDataSerializer jsonWriter = new JsonTelemetryDataSerializer(writer);
            item.serialize(jsonWriter);
            jsonWriter.close();
            writer.close();
            String asJson = new String(buffer.readByteArray(), Charsets.UTF_8);
            InternalLogger.INSTANCE.trace("StdOutChannel, TELEMETRY: %s",asJson);
        } catch (IOException ioe) {
        }
    }

    @Override
    public void stop(long timeout, TimeUnit timeUnit) {
    }

    @Override
    public void flush() {
    }
}
