package org.folio.rtaccache.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeignRequestInterceptor implements RequestInterceptor {

  private final FolioExecutionContext folioExecutionContext;

  @Override
  public void apply(RequestTemplate requestTemplate) {
    requestTemplate.target(folioExecutionContext.getOkapiUrl());
    requestTemplate.headers(folioExecutionContext.getOkapiHeaders());
  }
}
