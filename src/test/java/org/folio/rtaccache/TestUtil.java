package org.folio.rtaccache;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.folio.rtaccache.domain.dto.FolioCqlRequest;
import org.mockito.ArgumentMatcher;

public class TestUtil {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .setSerializationInclusion(Include.NON_NULL)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public static String readFileContentFromResources(String path) {
    try {
      ClassLoader classLoader = TestUtil.class.getClassLoader();
      URL url = classLoader.getResource(path);
      return IOUtils.toString(url, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @SneakyThrows
  public static <T> T asJsonResponse(Object obj) {
    String json = new ObjectMapper().writeValueAsString(obj);
    return JsonPath.read(json, "$");
  }

  @SneakyThrows
  public static String asString(Object value) {
    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
  }

  public static ArgumentMatcher<FolioCqlRequest> queryContains(String str) {
    return arg -> arg != null && arg.getQuery().contains(str);
  }
}
