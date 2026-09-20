package br.fiap.daiji.config;

import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class CorsConfiguration implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public CorsConfiguration(@Value("${app.cors.allowed-origins}") String origins) {
        allowedOrigins = Arrays.stream(origins.split(",", -1)).map(String::strip).toArray(String[]::new);
        for (String origin : allowedOrigins) {
            if (!origemValida(origin)) {
                throw new IllegalArgumentException("Configure CORS_ALLOWED_ORIGINS com origens HTTP/HTTPS explícitas, sem curinga, caminho ou credenciais.");
            }
        }
    }

    private static boolean origemValida(String origin) {
        try {
            URI uri = URI.create(origin);
            return !origin.contains("*") && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null
                    && "".equals(uri.getRawPath()) && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getPort() <= 65535;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept");
    }
}
