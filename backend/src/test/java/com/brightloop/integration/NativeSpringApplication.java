package com.brightloop.integration;

import com.brightloop.BrightloopApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

/** Spring 7's SpringExtension requires JUnit 6. Real app lifecycle keeps JUnit 5 compatible. */
final class NativeSpringApplication {
  private NativeSpringApplication() {}

  static ServletWebServerApplicationContext start() {
    return (ServletWebServerApplicationContext)
        new SpringApplicationBuilder(BrightloopApplication.class).run("--server.port=0");
  }
}
