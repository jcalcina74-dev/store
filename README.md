# Store API

Backend del catálogo de celulares. Spring Boot, Java 18 y PostgreSQL. El catálogo es público, paginado y se actualiza mediante SSE. Una cuenta administradora permite gestionar marcas, celulares y fotos WebP.

## Ejecutar en local

1. Instala Java 18 y crea una base PostgreSQL llamada `store`.
2. Desde la raíz del backend, copia `.env.example` a `.env` si aún no existe y completa las contraseñas. La contraseña inicial del administrador debe tener al menos 12 caracteres y como máximo 72 bytes UTF-8.
3. Ejecuta el backend desde esa misma carpeta.

```powershell
.\mvnw.cmd spring-boot:run
```

En IntelliJ, el directorio de trabajo debe ser la carpeta que contiene `pom.xml`. Spring Boot carga `.env` desde ese directorio y Flyway crea las tablas. Si prefieres iniciar PostgreSQL con Docker, ejecuta `docker compose up -d` antes de arrancar el backend.

## API

| Ruta | Uso |
| --- | --- |
| `GET /api/catalogo/celulares?page=0&size=12` | Catálogo paginado |
| `GET /api/catalogo/celulares/{id}` | Detalle del celular |
| `GET /api/catalogo/eventos` | Actualizaciones del catálogo |
| `GET /api/media/{id}` | Foto WebP |
| `/api/admin/**` | Gestión del catálogo con HTTP Basic |

Swagger está en `/swagger-ui/index.html`. Activa el perfil `dev` para consultar la documentación sin iniciar sesión. Las escrituras administrativas requieren HTTP Basic y `X-Admin-Request: true`.

La cuenta se crea al iniciar con una base vacía. Para actualizarla, configura `ADMIN_USERNAME` y `ADMIN_PASSWORD` y arranca una vez con `ADMIN_UPDATE_ON_START=true`. Retira esa variable después de verificar el acceso.

## Pruebas y despliegue

- Ejecuta `.\mvnw.cmd verify` para compilar y correr las pruebas.
- En Render, crea un Web Service con Docker, configura las variables del perfil cloud en Environment y usa /api/health como health check.
