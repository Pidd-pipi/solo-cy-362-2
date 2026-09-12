package com.generated.ldmurdergame.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** 测试 HTTP 辅助：返回状态码与 JSON，便于断言具体业务场景。 */
public final class ApiHelper {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ApiHelper() {
  }

  public record Response(int status, JsonNode json, String raw) {
    public String message() {
      return json.path("message").asText();
    }
  }

  public static Response call(MockMvc mockMvc, org.springframework.test.web.servlet.RequestBuilder builder)
      throws Exception {
    MvcResult result = mockMvc.perform(builder).andReturn();
    // MockMvc 在未显式指定响应字符集时默认按 ISO-8859-1 解码，会导致中文乱码；统一按 UTF-8 读取
    byte[] bytes = result.getResponse().getContentAsByteArray();
    String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    return new Response(result.getResponse().getStatus(),
        body.isEmpty() ? MAPPER.createObjectNode() : MAPPER.readTree(bytes), body);
  }

  public static Response getJson(MockMvc mvc, String uri) throws Exception {
    return call(mvc, MockMvcRequestBuilders.get(uri).accept(MediaType.APPLICATION_JSON));
  }

  public static Response post(MockMvc mvc, String uri, Object body) throws Exception {
    return call(mvc, MockMvcRequestBuilders.post(uri)
        .contentType(MediaType.APPLICATION_JSON).content(MAPPER.writeValueAsString(body)));
  }

  public static Response put(MockMvc mvc, String uri, Object body) throws Exception {
    return call(mvc, MockMvcRequestBuilders.put(uri)
        .contentType(MediaType.APPLICATION_JSON).content(MAPPER.writeValueAsString(body)));
  }

  public static Response patch(MockMvc mvc, String uri, Object body) throws Exception {
    return call(mvc, MockMvcRequestBuilders.patch(uri)
        .contentType(MediaType.APPLICATION_JSON).content(MAPPER.writeValueAsString(body)));
  }

  public static Response delete(MockMvc mvc, String uri) throws Exception {
    return call(mvc, MockMvcRequestBuilders.delete(uri));
  }

  /** 带查询参数的 DELETE：MockMvc 不会像真实 Tomcat 那样解码百分号编码，故用 param 传中文。 */
  public static Response deleteWithParam(MockMvc mvc, String uri, String name, String value) throws Exception {
    return call(mvc, MockMvcRequestBuilders.delete(uri).param(name, value));
  }

  /** 直接在数据库语义上创建一个启用剧本，返回其 id。 */
  public static long createScript(MockMvc mvc, String name, String genre, String difficulty,
      int duration, int min, int max) throws Exception {
    Response response = post(mvc, "/api/scripts", java.util.Map.of(
        "name", name, "genre", genre, "difficulty", difficulty,
        "durationMinutes", duration, "minPlayers", min, "maxPlayers", max));
    if (response.status() != 201) {
      throw new IllegalStateException("创建剧本失败: " + response.raw());
    }
    return response.json().path("id").asLong();
  }

  /** 创建未来场次，返回场次 id。 */
  public static long createSession(MockMvc mvc, long scriptId, String startTime, String host, int capacity)
      throws Exception {
    Response response = post(mvc, "/api/sessions", java.util.Map.of(
        "scriptId", scriptId, "startTime", startTime, "hostName", host, "capacity", capacity));
    if (response.status() != 201) {
      throw new IllegalStateException("创建场次失败: " + response.raw());
    }
    return response.json().path("id").asLong();
  }
}
