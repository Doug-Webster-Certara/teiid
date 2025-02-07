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

package org.teiid.query.util;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.junit.Test;
import org.teiid.logging.CommandLogMessage;

import static org.junit.Assert.*;

@SuppressWarnings("nls")
public class TestTeiidTracingUtil {

    @Test public void testTracing() {
        TeiidTracingUtil ttu = new TeiidTracingUtil();
        Tracer tracer = GlobalOpenTelemetry.getTracer("test");
        ttu.setTracer(tracer);
        assertNotNull(ttu.extractSpanContext("{\"spanid\":\"1\",\"traceid\":\"2\"}"));
        Context spanContext = ttu.extractSpanContext("corrupted");
        assertNull(spanContext);
        assertTrue(Span.current() == Span.getInvalid()); //should be null, no side effect just from extract

        Span span = ttu.buildSpan(new Options().tracingWithActiveSpanOnly(false), new CommandLogMessage(0, "", null, null, null, null, null, null, "", null), null);
        assertNotNull(span);

        span = ttu.buildSpan(new Options().tracingWithActiveSpanOnly(false), new CommandLogMessage(0, "", null, null, null, null, null, null, "", null), "{\"spanid\":\"1\",\"traceid\":\"2\"}");
        assertNotNull(span);
    }

    @Test public void testTracingEnabled() {
        TeiidTracingUtil ttu = new TeiidTracingUtil();
        Tracer tracer = GlobalOpenTelemetry.getTracer("test");
        ttu.setTracer(tracer);
        assertFalse(ttu.isTracingEnabled(new Options().tracingWithActiveSpanOnly(true), null));
    }

}
