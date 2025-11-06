package org.folio.rtaccache;

import feign.Client;
import feign.okhttp.OkHttpClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableFeignClients
public class RtacCacheApplication {

  public static void main(String[] args) {
    SpringApplication.run(RtacCacheApplication.class, args);
  }

  @Bean
  Client client() {
    return new OkHttpClient();
  }

}
