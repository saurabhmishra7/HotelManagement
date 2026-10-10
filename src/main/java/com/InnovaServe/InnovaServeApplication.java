package com.InnovaServe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InnovaServeApplication {

  public static void main(String[] args) {
    SpringApplication.run(InnovaServeApplication.class, args);
  }
}
