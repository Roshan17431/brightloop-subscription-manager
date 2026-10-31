package com.brightloop.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real HTTP, session, security-filter and PostgreSQL checks; use a disposable DB_URL. */
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ApiIntegrationTest {
  private static org.springframework.boot.web.server.servlet.context
          .ServletWebServerApplicationContext
      application;

  @BeforeAll
  static void startApplication() {
    application = NativeSpringApplication.start();
  }

  @AfterAll
  static void stopApplication() {
    if (application != null) application.close();
  }

  private final ObjectMapper json = new ObjectMapper();

  private String path(String path) {
    return "http://localhost:" + application.getWebServer().getPort() + path;
  }

  private String email() {
    return "test-" + UUID.randomUUID() + "@example.test";
  }

  private final String password = UUID.randomUUID() + "Aa!";

  private class Browser {
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    String token;
    String header;

    HttpResponse<String> request(String method, String endpoint, Object body, boolean csrf)
        throws Exception {
      if (csrf) {
        var response = request("GET", "/api/v1/auth/csrf", null, false);
        assertEquals(200, response.statusCode(), response.body());
        var data = json.readTree(response.body()).path("data");
        token = data.path("token").asText();
        header = data.path("headerName").asText();
      }
      var request =
          HttpRequest.newBuilder(URI.create(path(endpoint)))
              .header("Content-Type", "application/json");
      if (csrf) request.header(header, token);
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> register(String email) throws Exception {
      return request(
          "POST",
          "/api/v1/auth/register",
          Map.of("name", "Integration Customer", "email", email, "password", password),
          true);
    }

    void login(String email) throws Exception {
      var response =
          request("POST", "/api/v1/auth/login", Map.of("email", email, "password", password), true);
      assertEquals(200, response.statusCode(), response.body());
    }
  }

  @Test
  void longEmailCanPersistAnAuthenticatedSession() throws Exception {
    var browser = new Browser();
    String email =
        "long-" + UUID.randomUUID() + "@" + "a".repeat(60) + "." + "b".repeat(60) + ".test";
    assertEquals(201, browser.register(email).statusCode());
    browser.login(email);
    var me = browser.request("GET", "/api/v1/auth/me", null, false);
    assertEquals(200, me.statusCode(), me.body());
    assertEquals(email, json.readTree(me.body()).path("data").path("email").asText());
  }

  @Test
  void healthReportsDatabaseReady() throws Exception {
    var response = new Browser().request("GET", "/actuator/health", null, false);
    assertEquals(200, response.statusCode());
    assertEquals("UP", json.readTree(response.body()).path("status").asText());
  }

  @Test
  void unauthenticatedErrorUsesContract() throws Exception {
    var response = new Browser().request("GET", "/api/v1/auth/me", null, false);
    assertEquals(401, response.statusCode());
    JsonNode error = json.readTree(response.body());
    assertFalse(error.path("success").asBoolean());
    assertEquals(401, error.path("statusCode").asInt());
    assertFalse(error.path("errorCode").asText().isBlank());
    assertEquals("/api/v1/auth/me", error.path("path").asText());
  }

  @Test
  void registrationRequiresCsrfToken() throws Exception {
    var response =
        new Browser()
            .request(
                "POST",
                "/api/v1/auth/register",
                Map.of("name", "Test", "email", email(), "password", password),
                false);
    assertEquals(403, response.statusCode());
  }

  @Test
  void weakPasswordsAreRejected() throws Exception {
    for (String weak :
        List.of("Ab!12345", "alllowercase!", "ALLUPPERCASE!", "NoSpecialCharacter")) {
      var response =
          new Browser()
              .request(
                  "POST",
                  "/api/v1/auth/register",
                  Map.of("name", "Test", "email", email(), "password", weak),
                  true);
      assertEquals(400, response.statusCode(), "Password policy response: " + response.body());
    }
  }

  @Test
  void duplicateNormalizedEmailIsAConflict() throws Exception {
    var browser = new Browser();
    String email = email();
    assertEquals(201, browser.register(email).statusCode());
    var duplicate = browser.register(email.toUpperCase(Locale.ROOT));
    assertEquals(409, duplicate.statusCode(), duplicate.body());
  }

  @Test
  void loginMeLogoutUseRealSession() throws Exception {
    var browser = new Browser();
    String email = email();
    assertEquals(201, browser.register(email).statusCode());
    browser.login(email);
    var me = browser.request("GET", "/api/v1/auth/me", null, false);
    assertEquals(200, me.statusCode(), me.body());
    assertEquals(email, json.readTree(me.body()).path("data").path("email").asText());
    assertEquals("CUSTOMER", json.readTree(me.body()).path("data").path("role").asText());
    var logout = browser.request("POST", "/api/v1/auth/logout", Map.of(), true);
    assertTrue(logout.statusCode() == 200 || logout.statusCode() == 204, logout.body());
    assertEquals(401, browser.request("GET", "/api/v1/auth/me", null, false).statusCode());
  }

  @Test
  void customerCannotListCustomersCreateStaffOrMutateCatalog() throws Exception {
    var browser = new Browser();
    String email = email();
    assertEquals(201, browser.register(email).statusCode());
    browser.login(email);
    assertEquals(403, browser.request("GET", "/api/v1/customers", null, false).statusCode());
    assertEquals(
        403,
        browser
            .request(
                "POST",
                "/api/v1/staff",
                Map.of("name", "Forbidden", "email", email(), "password", password),
                true)
            .statusCode());
    assertEquals(
        403,
        browser
            .request(
                "POST",
                "/api/v1/products",
                Map.of(
                    "name",
                    "Forbidden",
                    "type",
                    "SOFTWARE",
                    "salesPrice",
                    10,
                    "costPrice",
                    1,
                    "status",
                    "ACTIVE",
                    "variants",
                    List.of()),
                true)
            .statusCode());
    assertEquals(
        403,
        browser
            .request(
                "POST",
                "/api/v1/plans",
                Map.of(
                    "name",
                    "Forbidden",
                    "price",
                    10,
                    "billingPeriod",
                    "MONTHLY",
                    "minimumQuantity",
                    1,
                    "status",
                    "ACTIVE",
                    "productIds",
                    List.of(1)),
                true)
            .statusCode());
  }

  @Test
  void wrongHttpMethodPreserves405() throws Exception {
    var response = new Browser().request("GET", "/api/v1/auth/login", null, false);
    assertEquals(405, response.statusCode(), response.body());
    assertEquals(405, json.readTree(response.body()).path("statusCode").asInt());
  }

  @Test
  void missingAuthenticatedRoutePreserves404() throws Exception {
    var browser = new Browser();
    String address = email();
    assertEquals(201, browser.register(address).statusCode());
    browser.login(address);
    var response = browser.request("GET", "/api/v1/does-not-exist", null, false);
    assertEquals(404, response.statusCode(), response.body());
    assertEquals(404, json.readTree(response.body()).path("statusCode").asInt());
  }
}
