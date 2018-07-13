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

package io.opencensus.contrib.jaxrs.metrics;

import io.opencensus.common.Clock;
import io.opencensus.common.Scope;
import io.opencensus.contrib.http.util.HttpMeasureConstants;
import io.opencensus.contrib.http.util.HttpTraceConstants;
import io.opencensus.implcore.common.MillisClock;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.tags.TagContext;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;
import io.opencensus.tags.Tagger;
import io.opencensus.tags.Tags;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.MessageEvent;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.propagation.SpanContextParseException;
import io.opencensus.trace.propagation.TextFormat;
import io.opencensus.trace.samplers.Samplers;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;

@Provider
// @Priority(Priorities.AUTHENTICATION - 500)
public class JaxrsMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final TagKey HTTP_SERVER_ROUTE = TagKey.create("http_server_route");

  private static final String OPENCENSUS_TRACE_SCOPE = "opencensus_trace_scope";
  private static final String OPENCENSUS_TAG_SCOPE = "opencensus_tag_scope";
  private static final String OPENCENSUS_START_NANOS = "opencensus_start_nanos";
  private static final Clock CLOCK = MillisClock.getInstance();

  private final Tracer tracer;
  private final Tagger tagger;
  private final StatsRecorder statsRecorder;
  private final TextFormat propagationFormat;

  @Context private ResourceInfo info;

  /**
   * Construct new instance of metrics filter.
   *
   * @param propagationFormat Representation of the trace propagation format.
   */
  public JaxrsMetricsFilter(TextFormat propagationFormat) {
    tracer = Tracing.getTracer();
    tagger = Tags.getTagger();
    statsRecorder = Stats.getStatsRecorder();
    this.propagationFormat = propagationFormat;
  }

  @Override
  @SuppressWarnings("MustBeClosedChecker")
  public void filter(ContainerRequestContext requestContext) {
    Optional<String> route = getRoute();
    String path = requestContext.getUriInfo().getPath();

    Scope tagScope =
        tagger
            .currentBuilder()
            .put(HTTP_SERVER_ROUTE, TagValue.create(route.orElse(path)))
            .put(
                HttpMeasureConstants.HTTP_SERVER_HOST,
                TagValue.create(requestContext.getHeaderString("host")))
            .put(HttpMeasureConstants.HTTP_SERVER_PATH, TagValue.create(path))
            .put(
                HttpMeasureConstants.HTTP_SERVER_METHOD,
                TagValue.create(requestContext.getMethod()))
            .buildScoped();
    requestContext.setProperty(OPENCENSUS_TAG_SCOPE, tagScope);

    SpanContext spanContext = null;
    try {
      spanContext =
          propagationFormat.extract(
              requestContext,
              new TextFormat.Getter<ContainerRequestContext>() {
                @Nullable
                @Override
                public String get(ContainerRequestContext request, String key) {
                  return request.getHeaderString(key);
                }
              });
    } catch (SpanContextParseException e) {
      // Ignore as it probably means no incoming span context
    }

    Span traceSpan =
        tracer
            .spanBuilderWithRemoteParent(route.orElse(path), spanContext)
            .setRecordEvents(true)
            .setSampler(Samplers.alwaysSample())
            .setSpanKind(Span.Kind.SERVER)
            .startSpan();
    route.ifPresent(
        r ->
            traceSpan.putAttribute(
                HttpTraceConstants.HTTP_ROUTE, AttributeValue.stringAttributeValue(r)));
    traceSpan.putAttribute(HttpTraceConstants.HTTP_PATH, AttributeValue.stringAttributeValue(path));
    traceSpan.putAttribute(
        HttpTraceConstants.HTTP_METHOD,
        AttributeValue.stringAttributeValue(requestContext.getMethod()));
    traceSpan.putAttribute(
        HttpTraceConstants.HTTP_HOST,
        AttributeValue.stringAttributeValue(requestContext.getHeaderString("host")));

    // Null check
    traceSpan.putAttribute(
        HttpTraceConstants.HTTP_USER_AGENT,
        AttributeValue.stringAttributeValue(requestContext.getHeaderString("user-agent")));
    if (requestContext.getLength() > -1) {
      traceSpan.addMessageEvent(
          MessageEvent.builder(MessageEvent.Type.RECEIVED, 0)
              .setUncompressedMessageSize(requestContext.getLength())
              .build());
    }

    traceSpan.addAnnotation("Handle incoming request.");

    requestContext.setProperty(OPENCENSUS_TRACE_SCOPE, tracer.withSpan(traceSpan));
    requestContext.setProperty(OPENCENSUS_START_NANOS, CLOCK.nowNanos());
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    if (requestContext.getProperty(OPENCENSUS_START_NANOS) == null) {
      // JAX-RS response filters are always invoked - we only want to record something if
      // request came through this filter
      return;
    }

    TagContext tagContext = tagger.getCurrentTagContext();
    TagContext tagContextStatus =
        tagger
            .toBuilder(tagContext)
            .put(
                HttpMeasureConstants.HTTP_SERVER_STATUS,
                TagValue.create(Integer.toString(responseContext.getStatus())))
            .build();

    long startTimeNanos = (long) requestContext.getProperty(OPENCENSUS_START_NANOS);
    statsRecorder
        .newMeasureMap()
        .put(HttpMeasureConstants.HTTP_SERVER_LATENCY, (CLOCK.nowNanos() - startTimeNanos) / 1E6)
        .put(HttpMeasureConstants.HTTP_SERVER_RECEIVED_BYTES, requestContext.getLength())
        .put(HttpMeasureConstants.HTTP_SERVER_SENT_BYTES, responseContext.getLength())
        .record(tagContextStatus);

    Span span = tracer.getCurrentSpan();
    span.putAttribute(
        HttpTraceConstants.HTTP_STATUS_CODE,
        AttributeValue.longAttributeValue(responseContext.getStatus()));
    if (responseContext.getLength() > -1) {
      span.addMessageEvent(
          MessageEvent.builder(MessageEvent.Type.SENT, 0)
              .setUncompressedMessageSize(responseContext.getLength())
              .build());
    }

    span.addAnnotation("Completed request processing.");
    span.setStatus(mapStatus(responseContext));

    ((Scope) requestContext.getProperty(OPENCENSUS_TAG_SCOPE)).close();
    ((Scope) requestContext.getProperty(OPENCENSUS_TRACE_SCOPE)).close();
    span.end();
  }

  /** Map HTTP status code according to the OpenCensus HTTP trace specification. */
  private static Status mapStatus(ContainerResponseContext responseContext) {
    int httpStatus = responseContext.getStatus();
    String reasonPhrase = responseContext.getStatusInfo().getReasonPhrase();
    if (httpStatus < 200) {
      return Status.UNKNOWN.withDescription(reasonPhrase);
    } else if (httpStatus < 400) {
      return Status.OK.withDescription(reasonPhrase);
    } else {
      switch (httpStatus) {
        case 400:
          return Status.INVALID_ARGUMENT.withDescription(reasonPhrase);
        case 401:
          return Status.UNAUTHENTICATED.withDescription(reasonPhrase);
        case 403:
          return Status.PERMISSION_DENIED.withDescription(reasonPhrase);
        case 404:
          return Status.NOT_FOUND.withDescription(reasonPhrase);
        case 429:
          return Status.RESOURCE_EXHAUSTED.withDescription(reasonPhrase);
        case 501:
          return Status.UNIMPLEMENTED.withDescription(reasonPhrase);
        case 503:
          return Status.UNAVAILABLE.withDescription(reasonPhrase);
        case 504:
          return Status.DEADLINE_EXCEEDED.withDescription(reasonPhrase);
        default:
          return Status.UNKNOWN.withDescription(reasonPhrase);
      }
    }
  }

  /** Get the route, i.e., JAX-RS template path. */
  private Optional<String> getRoute() {
    if (info.getResourceClass() != null) {
      UriBuilder uriBuilder;
      if (info.getResourceMethod() != null) {
        uriBuilder = UriBuilder.fromResource(info.getResourceClass());
      } else {
        uriBuilder =
            UriBuilder.fromMethod(info.getResourceClass(), info.getResourceMethod().getName());
      }
      return Optional.of(uriBuilder.toTemplate());
    }
    return Optional.empty();
  }
}
