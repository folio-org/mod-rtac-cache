package org.folio.rtaccache.config;

import lombok.RequiredArgsConstructor;
import org.folio.rtaccache.client.CirculationClient;
import org.folio.rtaccache.client.ConsortiaClient;
import org.folio.rtaccache.client.InventoryClient;
import org.folio.rtaccache.client.OrdersClient;
import org.folio.rtaccache.client.SearchClient;
import org.folio.rtaccache.client.SettingsClient;
import org.folio.rtaccache.client.UserClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfiguration {

  private final HttpServiceProxyFactory httpServiceProxyFactory;

  @Bean
  public CirculationClient circulationClient() {
    return httpServiceProxyFactory.createClient(CirculationClient.class);
  }

  @Bean
  public ConsortiaClient consortiaClient() {
    return httpServiceProxyFactory.createClient(ConsortiaClient.class);
  }

  @Bean
  public InventoryClient inventoryClient() {
    return httpServiceProxyFactory.createClient(InventoryClient.class);
  }

  @Bean
  public OrdersClient ordersClient() {
    return httpServiceProxyFactory.createClient(OrdersClient.class);
  }

  @Bean
  public SearchClient searchClient() {
    return httpServiceProxyFactory.createClient(SearchClient.class);
  }

  @Bean
  public SettingsClient settingsClient() {
    return httpServiceProxyFactory.createClient(SettingsClient.class);
  }

  @Bean
  public UserClient usersClient() {
    return httpServiceProxyFactory.createClient(UserClient.class);
  }
}

