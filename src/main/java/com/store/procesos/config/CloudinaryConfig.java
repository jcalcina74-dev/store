package com.store.procesos.config;

import com.cloudinary.Cloudinary;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.media-storage", havingValue = "cloudinary")
public class CloudinaryConfig {
  @Bean
  Cloudinary cloudinary(
      @Value("${app.cloudinary.cloud-name}") String cloud,
      @Value("${app.cloudinary.api-key}") String key,
      @Value("${app.cloudinary.api-secret}") String secret) {
    if (!cloud.matches("[a-zA-Z0-9_-]+") || key.isBlank() || secret.isBlank())
      throw new IllegalArgumentException("Configurar las credenciales de Cloudinary");
    return new Cloudinary(Map.of("cloud_name", cloud, "api_key", key,
        "api_secret", secret, "secure", true, "timeout", 30));
  }
}
