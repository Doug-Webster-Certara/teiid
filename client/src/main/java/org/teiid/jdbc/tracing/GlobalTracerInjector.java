/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.teiid.jdbc.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.teiid.jdbc.tracing.TracingHelper.Injector;

import java.util.HashMap;
import java.util.Map;

/**
 * Uses the opentelemetry library to create a json string representation of the span context
 * and provides a way to manipulate a static tracer without using the GlobalTracer registration
 */
public class GlobalTracerInjector implements Injector {

    private static Tracer TRACER = GlobalOpenTelemetry.getTracer("org.teiid.jdbc.tracing");

    @Override
    public String getSpanContext() {
        return getSpanContext(TRACER);
    }

    protected static String getSpanContext(Tracer tracer) {
        Span span = Span.current();
        if (span == null) {
            return null;
        }
        Map<String,String> spanMap = new HashMap<String, String>();
//        AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk().getPropagators().getTextMapPropagator().inject(Context.current(), Builtin.TEXT_MAP, new TextMapInjectAdapter(spanMap));

        //simple json creation
        StringBuilder json = new StringBuilder();
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : spanMap.entrySet()) {
            if (!first) {
                json.append(',');
            } else {
                first = false;
            }
            json.append('"').append(entry.getKey().replace("\"", "\\\"")) //$NON-NLS-1$ //$NON-NLS-2$
            .append("\":\"") //$NON-NLS-1$
            .append(entry.getValue().replace("\"", "\\\"")).append('"'); //$NON-NLS-1$ //$NON-NLS-2$
        }
        json.append('}');
        return json.toString();
    }

    /*
     * Used to workaround that the GlobalTracer can only be registered once.
     */

    public static Tracer getTracer() {
        return TRACER;
    }

    public static void setTracer(Tracer tracer) {
        TRACER = tracer;
    }

}
