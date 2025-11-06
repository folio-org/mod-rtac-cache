package org.folio.rtaccache.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.folio.rtaccache.client.InventoryClient;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.folio.rtaccache.domain.dto.HoldingsNoteType;
import org.folio.rtaccache.domain.dto.HoldingsNoteTypes;
import org.folio.rtaccache.domain.dto.LoanType;
import org.folio.rtaccache.domain.dto.LoanTypes;
import org.folio.rtaccache.domain.dto.Location;
import org.folio.rtaccache.domain.dto.Locations;
import org.folio.rtaccache.domain.dto.Loclib;
import org.folio.rtaccache.domain.dto.Loclibs;
import org.folio.rtaccache.domain.dto.MaterialType;
import org.folio.rtaccache.domain.dto.MaterialTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryReferenceDataServiceTest {

  @Mock
  InventoryClient inventoryClient;

  @InjectMocks
  InventoryReferenceDataService service;

  @Test
  void getLocationsMap_ok() {
    Location l1 = new Location("Name1", "C1", "I1", "CA1", "L1", UUID.randomUUID()).id("loc1");
    Location l2 = new Location("Name2", "C2", "I2", "CA2", "L2", UUID.randomUUID()).id("loc2");
    Locations wrapper = new Locations(Arrays.asList(l1, l2), 2);

    when(inventoryClient.getLocations(any(FolioCqlRequest.class))).thenReturn(wrapper);

    Map<String, Location> map = service.getLocationsMap();

    assertEquals(2, map.size());
    assertSame(l1, map.get("loc1"));
    assertSame(l2, map.get("loc2"));
    verify(inventoryClient).getLocations(ArgumentMatchers.any(FolioCqlRequest.class));
  }

  @Test
  void getLibraryMap_ok() {
    Loclib lib1 = new Loclib("Lib1", "LB1", "CA1").id("lib1");
    Loclib lib2 = new Loclib("Lib2", "LB2", "CA2").id("lib2");
    Loclibs wrapper = new Loclibs(Arrays.asList(lib1, lib2), 2);

    when(inventoryClient.getLibraries(any(FolioCqlRequest.class))).thenReturn(wrapper);

    Map<String, Loclib> map = service.getLibraryMap();

    assertEquals(2, map.size());
    assertSame(lib1, map.get("lib1"));
    assertSame(lib2, map.get("lib2"));
  }

  @Test
  void getMaterialTypesMap_ok() {
    MaterialType m1 = new MaterialType("Mat1").id("mt1");
    MaterialType m2 = new MaterialType("Mat2").id("mt2");
    MaterialTypes wrapper = new MaterialTypes(Arrays.asList(m1, m2), 2);

    when(inventoryClient.getMaterialTypes(any(FolioCqlRequest.class))).thenReturn(wrapper);

    Map<String, MaterialType> map = service.getMaterialTypesMap();

    assertEquals(2, map.size());
    assertSame(m1, map.get("mt1"));
    assertSame(m2, map.get("mt2"));
  }

  @Test
  void getLoanTypesMap_ok() {
    LoanType lt1 = new LoanType("Loan1").id("lt1");
    LoanType lt2 = new LoanType("Loan2").id("lt2");
    LoanTypes wrapper = new LoanTypes(Arrays.asList(lt1, lt2), 2);

    when(inventoryClient.getLoanTypes(any(FolioCqlRequest.class))).thenReturn(wrapper);

    Map<String, LoanType> map = service.getLoanTypesMap();

    assertEquals(2, map.size());
    assertSame(lt1, map.get("lt1"));
    assertSame(lt2, map.get("lt2"));
  }

  @Test
  void getHoldingsNoteTypesMap_ok() {
    HoldingsNoteType h1 = new HoldingsNoteType("Note1", "folio").id("hn1");
    HoldingsNoteType h2 = new HoldingsNoteType("Note2", "local").id("hn2");
    HoldingsNoteTypes wrapper = new HoldingsNoteTypes(Arrays.asList(h1, h2), 2);

    when(inventoryClient.getHoldingsNoteTypes(any(FolioCqlRequest.class))).thenReturn(wrapper);

    Map<String, HoldingsNoteType> map = service.getHoldingsNoteTypesMap();

    assertEquals(2, map.size());
    assertSame(h1, map.get("hn1"));
    assertSame(h2, map.get("hn2"));
  }

  @Test
  void getLoanTypesMap_duplicateIds_throws() {
    LoanType lt1 = new LoanType("Loan1").id("dup");
    LoanType lt2 = new LoanType("Loan2").id("dup"); // duplicate id
    LoanTypes wrapper = new LoanTypes(Arrays.asList(lt1, lt2), 2);

    when(inventoryClient.getLoanTypes(any(FolioCqlRequest.class))).thenReturn(wrapper);

    assertThrows(IllegalStateException.class, () -> service.getLoanTypesMap());
  }
}
