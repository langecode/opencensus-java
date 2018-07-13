/*
 * Copyright 2018, OpenCensus Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opencensus.contrib.http.util;

/**
 * A helper class which holds OpenCensus's default HTTP trace keys.
 *
 * @since 0.16.0
 * @see <a href=https://github.com/census-instrumentation/opencensus-specs/blob/master/trace/HTTP.md>HTTP Trace</a>
 */
public final class HttpTraceConstants {

    private HttpTraceConstants() {}

    /**
     * Attribute key for request URL host.
     */
    public static final String HTTP_HOST = "http.host";

    /**
     * Attribute key for request URL method.
     */
    public static final String HTTP_METHOD = "http.method";

    /**
     * Attribute key for request URL path.
     */
    public static final String HTTP_PATH = "http.path";

    /**
     * Attribute key for matched request URL route.
     */
    public static final String HTTP_ROUTE = "http.route";

    /**
     * Attribute key for request user-agent.
     */
    public static final String HTTP_USER_AGENT = "http.user_agent";

    /**
     * Attribute key for response status code.
     */
    public static final String HTTP_STATUS_CODE = "http.status_code";

}
