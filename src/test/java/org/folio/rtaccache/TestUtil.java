package org.folio.rtaccache;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.mockito.ArgumentMatcher;
import tools.jackson.databind.ObjectMapper;

public class TestUtil {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

  public static ArgumentMatcher<Map<String, String>> queryContains(String str) {
    return arg -> arg != null && arg.get("query") != null && arg.get("query").contains(str);
  }
}
