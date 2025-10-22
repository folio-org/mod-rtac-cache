package org.folio.rtaccache.configuration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import feign.RequestTemplate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FeignRequestInterceptorTest {
  @Mock
  FolioExecutionContext folioExecutionContext;

  @Mock
  RequestTemplate requestTemplate;

  @Test
  void apply_setsTargetAndHeaders() {
    String okapiUrl = "http://okapi.example.com";
    Map<String, Collection<String>> okapiHeaders = Map.of(
      "X-Okapi-Tenant", List.of("diku"),
      "X-Okapi-Token", List.of("test-token")
    );

    when(folioExecutionContext.getOkapiUrl()).thenReturn(okapiUrl);
    when(folioExecutionContext.getOkapiHeaders()).thenReturn(okapiHeaders);

    FeignRequestInterceptor interceptor = new FeignRequestInterceptor(folioExecutionContext);

    interceptor.apply(requestTemplate);

    verify(requestTemplate).target(okapiUrl);
    verify(requestTemplate).headers(okapiHeaders);
    verifyNoMoreInteractions(requestTemplate);

    verify(folioExecutionContext).getOkapiUrl();
    verify(folioExecutionContext).getOkapiHeaders();
    verifyNoMoreInteractions(folioExecutionContext);
  }
}
