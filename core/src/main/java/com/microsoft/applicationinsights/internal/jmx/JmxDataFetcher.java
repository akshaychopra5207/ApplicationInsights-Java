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

package com.microsoft.applicationinsights.internal.jmx;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;

import com.microsoft.applicationinsights.internal.logger.InternalLogger;

/**
 * A utility class that knows how to fetch JMX data.
 * <p>
 * Created by gupele on 3/15/2015.
 */
public class JmxDataFetcher {

    /**
     * Gets an object name and its attributes to fetch and will return the data.
     *
     * @param objectName The object name to search.
     * @param attributes The attributes that 'belong' to the object name.
     * @return A map that represent each attribute: the key is the displayed name for that attribute
     * and the value is a list of values found
     * @throws Exception In case the object name is not found.
     */
    public static Map<String, Collection<Object>> fetch(String objectName, Collection<JmxAttributeData> attributes)
            throws Exception {
        Map<String, Collection<Object>> result = new HashMap<String, Collection<Object>>();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> objects = server.queryNames(new ObjectName(objectName), null);
        if (objects.isEmpty()) {
            String errorMsg = String.format("Cannot find object name '%s'", objectName);
            throw new IllegalArgumentException(errorMsg);
        }

        for (JmxAttributeData attribute : attributes) {
            try {
                Collection<Object> resultForAttribute = fetch(server, objects, attribute.name);
                result.put(attribute.displayName, resultForAttribute);
            } catch (Exception e) {
                InternalLogger.INSTANCE
                        .error("Failed to fetch JMX object '%s' with attribute '%s': '%s'", objectName, attribute.name,
                                e.toString());
                throw e;
            }
        }

        return result;
    }

    private static Collection<Object> fetch(MBeanServer server, Set<ObjectName> objects, String attributeName)
            throws AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException {
        ArrayList<Object> result = new ArrayList<Object>();

        String[] inners = attributeName.split("\\.");

        for (ObjectName object : objects) {

            Object value;

            if (inners.length == 1) {
                value = server.getAttribute(object, attributeName);
            } else {
                value = server.getAttribute(object, inners[0]);
                if (value != null) {
                    value = ((CompositeData) value).get(inners[1]);
                }
            }
            if (value != null) {
                result.add(value);
            }
        }

        return result;
    }

    private JmxDataFetcher() {
    }
}
