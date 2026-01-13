package org.folio.rtaccache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.folio.rtaccache.client.ConsortiaClient;
import org.folio.rtaccache.client.UsersClient;
import org.folio.rtaccache.domain.dto.ConsortiaTenants;
import org.folio.rtaccache.domain.dto.ConsortiaTenantsTenantsInner;
import org.folio.rtaccache.domain.dto.UserTenants;
import org.folio.rtaccache.domain.dto.UserTenantsUserTenantsInner;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsortiaServiceTest {

  @Mock
  private ConsortiaClient consortiaClient;
  @Mock
  private UsersClient usersClient;
  @Mock
  private FolioExecutionContext folioExecutionContext;

  @InjectMocks
  private ConsortiaService consortiaService;

  @Test
  void isCentralTenant_shouldReturnTrue_whenTenantIsCentral() {
    var centralTenantId = "central_tenant";
    var userTenants = new UserTenants().totalRecords(1)
      .userTenants(List.of(new UserTenantsUserTenantsInner().centralTenantId(centralTenantId)));

    when(folioExecutionContext.getTenantId()).thenReturn(centralTenantId);
    when(usersClient.getUserTenants()).thenReturn(userTenants);

    var result = consortiaService.isCentralTenant();

    assertThat(result).isTrue();
  }

  @Test
  void isCentralTenant_shouldReturnFalse_whenTenantIsNotCentral() {
    var centralTenantId = "central_tenant";
    var memberTenantId = "member_tenant";
    var userTenants = new UserTenants().totalRecords(1)
      .userTenants(List.of(new UserTenantsUserTenantsInner().centralTenantId(centralTenantId)));

    when(folioExecutionContext.getTenantId()).thenReturn(memberTenantId);
    when(usersClient.getUserTenants()).thenReturn(userTenants);

    var result = consortiaService.isCentralTenant();

    assertThat(result).isFalse();
  }

  @Test
  void isCentralTenant_shouldReturnFalse_whenNoUserTenants() {
    var userTenants = new UserTenants().totalRecords(0);
    when(usersClient.getUserTenants()).thenReturn(userTenants);

    var result = consortiaService.isCentralTenant();

    assertThat(result).isFalse();
  }

  @Test
  void getConsortiaTenants_shouldReturnListOfTenantIds() {
    var consortiumId = UUID.randomUUID().toString();
    var tenantId1 = "tenant1";
    var tenantId2 = "tenant2";
    var userTenants = new UserTenants().totalRecords(1)
      .userTenants(List.of(new UserTenantsUserTenantsInner().consortiumId(consortiumId)));
    var consortiaTenants = new ConsortiaTenants().tenants(
      List.of(new ConsortiaTenantsTenantsInner().id(tenantId1), new ConsortiaTenantsTenantsInner().id(tenantId2)));

    when(usersClient.getUserTenants()).thenReturn(userTenants);
    when(consortiaClient.getConsortiaTenants(consortiumId, ConsortiaService.CONSORTIA_TENANTS_LIMIT))
      .thenReturn(consortiaTenants);

    var result = consortiaService.getConsortiaTenants();

    assertThat(result).containsExactlyInAnyOrder(tenantId1, tenantId2);
  }

  @Test
  void getConsortiaTenants_shouldReturnEmptyList_whenNoUserTenants() {
    var userTenants = new UserTenants().totalRecords(0);
    when(usersClient.getUserTenants()).thenReturn(userTenants);

    var result = consortiaService.getConsortiaTenants();

    assertThat(result).isEmpty();
  }

  @Test
  void getConsortiaTenants_shouldReturnEmptyList_whenNoConsortiaTenants() {
    var consortiumId = UUID.randomUUID().toString();
    var userTenants = new UserTenants().totalRecords(1)
      .userTenants(List.of(new UserTenantsUserTenantsInner().consortiumId(consortiumId)));
    var consortiaTenants = new ConsortiaTenants().tenants(Collections.emptyList());

    when(usersClient.getUserTenants()).thenReturn(userTenants);
    when(consortiaClient.getConsortiaTenants(consortiumId, ConsortiaService.CONSORTIA_TENANTS_LIMIT))
      .thenReturn(consortiaTenants);

    var result = consortiaService.getConsortiaTenants();

    assertThat(result).isEmpty();
  }

  @Test
  void getCentralTenantId_shouldReturnCentralTenantId() {
    var consortiumId = UUID.randomUUID().toString();
    var centralTenantId = "central_tenant";
    var userTenants = new UserTenants().totalRecords(1)
      .userTenants(List.of(new UserTenantsUserTenantsInner().consortiumId(consortiumId)));
    var consortiaTenants = new ConsortiaTenants().tenants(
      List.of(new ConsortiaTenantsTenantsInner().id(centralTenantId).isCentral(true),
        new ConsortiaTenantsTenantsInner().id("member_tenant").isCentral(false)));

    when(usersClient.getUserTenants()).thenReturn(userTenants);
    when(consortiaClient.getConsortiaTenants(consortiumId, ConsortiaService.CONSORTIA_TENANTS_LIMIT))
      .thenReturn(consortiaTenants);

    var result = consortiaService.getCentralTenantId();

    assertThat(result).hasValue(centralTenantId);
  }

  @Test
  void getCentralTenantId_shouldReturnEmpty_whenNoUserTenants() {
    var userTenants = new UserTenants().totalRecords(0);
    when(usersClient.getUserTenants()).thenReturn(userTenants);

    var result = consortiaService.getCentralTenantId();

    assertThat(result).isEmpty();
  }

  @Test
  void getCentralTenantId_shouldReturnEmpty_whenNoCentralTenant() {
    var consortiumId = UUID.randomUUID().toString();
    var userTenants = new UserTenants().totalRecords(1)
      .userTenants(List.of(new UserTenantsUserTenantsInner().consortiumId(consortiumId)));
    var consortiaTenants = new ConsortiaTenants().tenants(
      List.of(new ConsortiaTenantsTenantsInner().id("member_tenant").isCentral(false)));

    when(usersClient.getUserTenants()).thenReturn(userTenants);
    when(consortiaClient.getConsortiaTenants(consortiumId, ConsortiaService.CONSORTIA_TENANTS_LIMIT))
      .thenReturn(consortiaTenants);

    var result = consortiaService.getCentralTenantId();

    assertThat(result).isEmpty();
  }
}
