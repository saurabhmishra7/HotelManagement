package com.InnovaServe.api;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Customizes the server port for Zoho Catalyst deployment.
 * Catalyst provides the port via the environment variable X_ZOHO_CATALYST_LISTEN_PORT.
 * If not set (local dev), defaults to 9000.
 */
@Configuration
public class ServerPortCustomizer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        String portEnv = System.getenv("X_ZOHO_CATALYST_LISTEN_PORT");
        int listenPort = 9000; // Default port for local development

        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                listenPort = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                System.err.println("Warning: Invalid value for X_ZOHO_CATALYST_LISTEN_PORT: " + portEnv + ". Using default port " + listenPort);
            }
        }

        factory.setPort(listenPort);
    }
}