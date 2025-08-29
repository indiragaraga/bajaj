package com.example.bfh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

record GenerateWebhookRequest(String name, String regNo, String email) {}
record GenerateWebhookResponse(String webhook, String accessToken) {}
record SubmitSolutionRequest(String finalQuery) {}

@SpringBootApplication
public class App implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(App.class);

  @Value("${app.base-url}")   String baseUrl;
  @Value("${app.name}")       String name;
  @Value("${app.reg-no}")     String regNo;
  @Value("${app.email}")      String email;
  @Value("${app.final-sql:}") String finalSql;

  public static void main(String[] args) { SpringApplication.run(App.class, args); }

  @Override public void run(String... args) {
    try {
      // 1) Generate webhook on startup
      var api = WebClient.builder().baseUrl(baseUrl).build();
      var gen = api.post()
          .uri("/hiring/generateWebhook/JAVA")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GenerateWebhookRequest(name, regNo, email))
          .retrieve()
          .bodyToMono(GenerateWebhookResponse.class)
          .block();

      if (gen == null || gen.webhook() == null || gen.accessToken() == null)
        throw new IllegalStateException("Invalid generateWebhook response: " + gen);

      log.info("Generated webhook: {}", gen.webhook());

      if (finalSql == null || finalSql.isBlank())
        throw new IllegalStateException("Set app.final-sql in application.yml to your final SQL!");

      WebClient.create()
          .post()
          .uri(gen.webhook())
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", gen.accessToken())
          .bodyValue(new SubmitSolutionRequest(finalSql.trim()))
          .retrieve()
          .toBodilessEntity()
          .block();

      log.info("Submitted successfully.");
    } catch (Exception e) {
      log.error(" Failed", e);
      System.exit(1);
    } finally {
      System.exit(0);
    }
  }
}
