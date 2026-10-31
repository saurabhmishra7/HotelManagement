package com.InnovaServe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InnovaServeApplication {

  public static void main(String[] args) {
    String appSailPort = System.getenv("X_ZOHO_CATALYST_LISTEN_PORT");
    if (appSailPort == null || appSailPort.isBlank()) {
      SpringApplication.run(InnovaServeApplication.class, args);
      return;
    }

    AppSailStartupProxy proxy;
    try {
      proxy = AppSailStartupProxy.start(Integer.parseInt(appSailPort));
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Could not open the AppSail listening port", exception);
    }

    String[] internalArgs = java.util.Arrays.copyOf(args, args.length + 2);
    internalArgs[args.length] = "--server.port=" + proxy.internalPort();
    internalArgs[args.length + 1] = "--server.address=127.0.0.1";
    try {
      SpringApplication application = new SpringApplication(InnovaServeApplication.class);
      application.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> proxy.markReady());
      application.run(internalArgs);
    } catch (RuntimeException | Error exception) {
      proxy.close();
      throw exception;
    }
  }
}
