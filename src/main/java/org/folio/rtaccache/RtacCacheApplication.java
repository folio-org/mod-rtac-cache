package org.folio.rtaccache;

import feign.Client;
import feign.Request;
import feign.http2client.Http2Client;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableFeignClients
@EnableCaching
public class RtacCacheApplication {

  public static void main(String[] args) {
    SpringApplication.run(RtacCacheApplication.class, args);
  }

  @Bean
  Client client() {
    var options = new Request.Options();
    return new Http2Client(options);
  }

}
