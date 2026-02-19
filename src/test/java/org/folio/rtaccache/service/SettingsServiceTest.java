package org.folio.rtaccache.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.folio.rtaccache.client.SettingsClient;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.Settings;
import org.folio.rtaccache.domain.dto.SettingsItemsInner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

  @Mock
  private SettingsClient settingsClient;

  @InjectMocks
  private SettingsService settingsService;

  @Test
  void getLoanTenant_shouldReturnValueFromSettings() {
    // given
    var item = new SettingsItemsInner().value("centralTenant");
    var settings = new Settings(List.of(item));

    when(settingsClient.getSettings(any(FolioCqlRequest.class))).thenReturn(settings);

    // when
    var result = settingsService.getLoanTenant();

    // then
    assertEquals("centralTenant", result);
    verify(settingsClient).getSettings(any(FolioCqlRequest.class));
  }

  @Test
  void getLoanTenant_shouldReturnNullWhenNoItems() {
    // given
    var settings = new Settings(List.of());
    when(settingsClient.getSettings(any(FolioCqlRequest.class))).thenReturn(settings);

    // when
    var result = settingsService.getLoanTenant();

    // then
    assertNull(result);
    verify(settingsClient).getSettings(any(FolioCqlRequest.class));
  }

  @Test
  void getLoanTenant_shouldReturnNullWhenSettingsNull() {
    // given
    when(settingsClient.getSettings(any(FolioCqlRequest.class))).thenReturn(null);

    // when
    var result = settingsService.getLoanTenant();

    // then
    assertNull(result);
    verify(settingsClient).getSettings(any(FolioCqlRequest.class));
  }
}
