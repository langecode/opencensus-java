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

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opencensus.contrib.http.util.HttpViews;
import io.opencensus.exporter.stats.prometheus.PrometheusStatsCollector;
import io.opencensus.exporter.trace.logging.LoggingTraceExporter;
import io.opencensus.stats.Stats;
import io.opencensus.stats.ViewManager;
import io.opencensus.trace.Tracing;
import io.prometheus.client.CollectorRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class JaxrsMetricsFilterTest {

  private static final Logger logger = Logger.getLogger(JaxrsMetricsFilterTest.class.getName());

  @Mock ResourceInfo info;

  @InjectMocks
  JaxrsMetricsFilter filter =
      new JaxrsMetricsFilter(Tracing.getPropagationComponent().getB3Format());

  @BeforeClass
  public static void registration() {
    LoggingTraceExporter.register();
    PrometheusStatsCollector.createAndRegister();
    HttpViews.registerAllServerViews();
  }

  @Test
  public void test() throws Exception {
    ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getPath()).thenReturn("/some/path");

    ContainerRequestContext request = mock(ContainerRequestContext.class);
    when(request.getHeaderString("host")).thenReturn("my.domain");
    when(request.getHeaderString("user-agent"))
        .thenReturn(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_4) AppleWebKit/537.36 (KHTML, like Gecko)"
                + " Chrome/67.0.3396.99 Safari/537.36");
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(request.getMethod()).thenReturn("GET");
    Answer<Void> setPropertyClosure =
        invocation -> {
          data.put((String) invocation.getArguments()[0], invocation.getArguments()[1]);
          return null;
        };
    doAnswer(setPropertyClosure).when(request).setProperty(anyString(), anyObject());
    filter.filter(request);

    Response.StatusType httpStatusType = mock(Response.StatusType.class);
    when(httpStatusType.getReasonPhrase()).thenReturn("Unit Test");

    ContainerResponseContext response = mock(ContainerResponseContext.class);
    when(request.getProperty(anyString()))
        .then(invocation -> data.get(invocation.getArguments()[0]));
    when(response.getStatus()).thenReturn(200);
    when(response.getStatusInfo()).thenReturn(httpStatusType);

    filter.filter(request, response);

    logger.info("Wait");
    Thread.sleep(5100);

    CollectorRegistry.defaultRegistry.metricFamilySamples();
    ViewManager viewManager = Stats.getViewManager();
    viewManager
        .getAllExportedViews()
        .stream()
        .forEach(
            view ->
                logger.info(
                    String.format(
                        "Recorded stats for %s:\n %s",
                        view.getName(), viewManager.getView(view.getName()))));
  }
}
