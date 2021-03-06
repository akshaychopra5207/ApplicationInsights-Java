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

package com.microsoft.applicationinsights.internal.agent;

import com.microsoft.applicationinsights.internal.util.ThreadLocalCleaner;

@Deprecated
public enum AgentConnector {
    INSTANCE;

    @Deprecated
    enum RegistrationType {
        NONE,
        WEB,
        SELF
    }

    @Deprecated
    public static class RegistrationResult {
        private final String key;
        private final ThreadLocalCleaner cleaner;

        public RegistrationResult(String key, ThreadLocalCleaner cleaner) {
            this.key = key;
            this.cleaner = cleaner;
        }

        public String getKey() {
            return key;
        }

        public ThreadLocalCleaner getCleaner() {
            return cleaner;
        }
    }

    @Deprecated
    public synchronized RegistrationResult register(ClassLoader classLoader, String name) {
        return new RegistrationResult("", new ThreadLocalCleanerNop());
    }

    @Deprecated
    public synchronized boolean registerSelf() {
        return false;
    }

    @Deprecated
    public RegistrationResult universalAgentRegisterer() {
        return new RegistrationResult("", new ThreadLocalCleanerNop());
    }

    @Deprecated
    public void unregisterAgent() {
    }

    private static class ThreadLocalCleanerNop implements ThreadLocalCleaner {
        @Override public void clean() {
        }
    }
}
