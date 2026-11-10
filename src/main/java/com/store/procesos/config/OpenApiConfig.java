package com.store.procesos.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class OpenApiConfig {
  @Bean
  OpenAPI api() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Store - Catalogo de celulares")
                .version("v1")
                .description(
                    "Catalogo publico paginado. Una sola cuenta ADMIN para gestion. Moneda BOB. En"
                        + " escrituras enviar X-Admin-Request: true. Imagenes JPEG/PNG/WebP"
                        + " estaticas hasta 5 MiB, convertidas a WebP."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "basicAuth",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
  }

  @Bean
  OpenApiCustomizer adminHeader() {
    return api ->
        api.getPaths()
            .forEach(
                (path, item) ->
                    item.readOperationsMap()
                        .forEach(
                            (method, op) -> {
                              if (path.startsWith("/api/admin/") && !method.name().equals("GET")) {
                                op.addParametersItem(
                                    new Parameter()
                                        .in("header")
                                        .name(SecurityConfig.ADMIN_HEADER)
                                        .required(true)
                                        .description("Proteccion CSRF: enviar siempre true")
                                        .schema(
                                            new StringSchema()
                                                ._default("true")
                                                .addEnumItem("true")));
                              }
                              op.getResponses()
                                  .addApiResponse(
                                      "400",
                                      new ApiResponse()
                                          .description("Parametros o cuerpo invalidos"));
                              op.getResponses()
                                  .addApiResponse(
                                      "404",
                                      new ApiResponse()
                                          .description("Recurso inexistente o no publicado"));
                              if (path.startsWith("/api/admin/")) {
                                op.getResponses()
                                    .addApiResponse(
                                        "401",
                                        new ApiResponse()
                                            .description("Credenciales ausentes o incorrectas"));
                                op.getResponses()
                                    .addApiResponse(
                                        "403",
                                        new ApiResponse()
                                            .description(
                                                "Acceso denegado o falta X-Admin-Request"));
                                op.getResponses()
                                    .addApiResponse(
                                        "409",
                                        new ApiResponse()
                                            .description(
                                                "Duplicado, relacion existente o conflicto"
                                                    + " concurrente"));
                              }
                            }));
  }
}
