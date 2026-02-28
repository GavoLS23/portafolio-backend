# Portafolio Personal — Backend

API REST + WebSocket para el portafolio personal. Construida con Scala 3 y programación funcional pura.

## Stack tecnológico

| Capa             | Tecnología                          |
|------------------|-------------------------------------|
| Lenguaje         | Scala 3.3.5                         |
| HTTP             | Http4s 0.23 (Ember)                 |
| Endpoints / Docs | Tapir 1.9 + Swagger UI              |
| Base de datos    | PostgreSQL + Doobie 1.0 + HikariCP  |
| Migraciones      | Flyway 10.4                         |
| JSON             | Circe 0.14                          |
| Config           | Ciris 3.6 (variables de entorno)    |
| Auth             | JWT (jwt-scala) + BCrypt (jbcrypt)  |
| Almacenamiento   | AWS S3 (prod) / disco local (dev)   |
| Efectos          | Cats Effect 3.5                     |
| Logging          | Log4cats + Logback                  |
| Build            | SBT 1.12 + sbt-assembly             |

## Arquitectura

```
com.portafolio
├── Main.scala                          ← IOApp, punto de entrada
├── config/
│   └── AppConfig.scala                 ← Config via Ciris (env vars)
├── domain/
│   ├── common/
│   │   ├── Ids.scala                   ← Opaque types para todos los IDs
│   │   ├── Language.scala              ← enum Es | En
│   │   ├── Pagination.scala
│   │   └── errors/AppError.scala       ← ADT de errores de dominio
│   ├── auth/User.scala
│   ├── project/Project.scala
│   ├── blog/BlogPost.scala
│   ├── media/Media.scala
│   └── technology/Technology.scala
├── infrastructure/
│   ├── db/Database.scala               ← HikariCP transactor + Flyway
│   ├── aws/S3Service.scala             ← Implementación S3 de StorageService
│   └── storage/
│       ├── StorageService.scala        ← Trait genérico de almacenamiento
│       └── LocalStorageService.scala   ← Implementación local (dev)
├── repository/                         ← Doobie: 5 repositorios
├── service/                            ← Lógica de negocio: 5 servicios
└── http/
    ├── HttpServer.scala                ← Ember + CORS + Tapir + WS
    ├── endpoints/                      ← Tapir endpoints (5 archivos)
    ├── routes/
    │   └── LocalUploadRoutes.scala     ← Rutas de upload local (solo dev)
    ├── middleware/AuthMiddleware.scala  ← Validación JWT
    └── websocket/PreviewWebSocket.scala
```

## Requisitos

- Java 21+
- SBT 1.12+
- PostgreSQL 15+ (corriendo localmente o en Docker)

## Configuración

Copia `.env.example` a `.env` y rellena los valores:

```bash
cp .env.example .env
```

### Variables de entorno

| Variable               | Requerida    | Default              | Descripción                              |
|------------------------|--------------|----------------------|------------------------------------------|
| `DB_URL`               | Siempre      | —                    | JDBC URL de PostgreSQL                   |
| `DB_USER`              | Siempre      | —                    | Usuario de PostgreSQL                    |
| `DB_PASSWORD`          | Siempre      | —                    | Contraseña de PostgreSQL                 |
| `DB_SCHEMA`            | No           | `portafolio`         | Schema de PostgreSQL                     |
| `DB_POOL_SIZE`         | No           | `10`                 | Tamaño del pool HikariCP                 |
| `JWT_SECRET`           | Siempre      | —                    | Clave secreta para firmar JWT            |
| `JWT_EXPIRATION_HOURS` | No           | `24`                 | Validez del token en horas               |
| `ADMIN_EMAIL`          | Siempre      | —                    | Email del administrador inicial          |
| `ADMIN_PASSWORD`       | Siempre      | —                    | Contraseña del administrador inicial     |
| `APP_ENV`              | No           | `development`        | `development` o `production`             |
| `SERVER_HOST`          | No           | `0.0.0.0`            | Host del servidor                        |
| `SERVER_PORT`          | No           | `8080`               | Puerto del servidor                      |
| `ALLOWED_ORIGINS`      | No           | `http://localhost:4200` | CORS origins (separados por coma)     |
| `LOCAL_UPLOADS_DIR`    | No           | `./uploads`          | Directorio local para archivos (dev)     |
| `BASE_URL`             | No           | `http://localhost:8080` | URL base del servidor (dev)           |
| `AWS_ACCESS_KEY_ID`    | Solo prod    | —                    | Credenciales AWS                         |
| `AWS_SECRET_ACCESS_KEY`| Solo prod    | —                    | Credenciales AWS                         |
| `AWS_REGION`           | Solo prod    | —                    | Región AWS (e.g. `us-east-1`)            |
| `AWS_S3_BUCKET`        | Solo prod    | —                    | Nombre del bucket S3                     |

### Permisos en PostgreSQL

El schema `portafolio` se crea automáticamente con Flyway, pero el usuario de la app debe tener permisos. Ejecuta esto **una sola vez** como superusuario de PostgreSQL:

```sql
CREATE SCHEMA IF NOT EXISTS portafolio;
GRANT ALL ON SCHEMA portafolio TO "tu-usuario-db";
ALTER DEFAULT PRIVILEGES IN SCHEMA portafolio GRANT ALL ON TABLES TO "tu-usuario-db";
ALTER DEFAULT PRIVILEGES IN SCHEMA portafolio GRANT ALL ON SEQUENCES TO "tu-usuario-db";
```

## Desarrollo local

```bash
# 1. Asegúrate de que PostgreSQL está corriendo

# 2. Carga las variables de entorno y lanza el servidor
set -a && source .env && set +a && sbt run
```

El servidor arranca en `http://localhost:8080`.
La **Swagger UI** está disponible en `http://localhost:8080/`.

### Almacenamiento en desarrollo

Con `APP_ENV=development` (default), los archivos se guardan en `./uploads/` localmente.
No se necesitan credenciales de AWS. El flujo de upload del frontend funciona igual que en producción.

## Producción

### Compilar fat JAR

```bash
sbt assembly
# Genera: target/scala-3.3.5/portafolio-backend-assembly-0.1.0-SNAPSHOT.jar
```

### Docker

```bash
# Construir imagen
docker build -t portafolio-backend .

# Ejecutar (variables de entorno via --env-file)
docker run --env-file .env -p 8080:8080 portafolio-backend
```

```bash
# O con docker-compose (solo el backend, PostgreSQL externo)
docker compose up -d
```

## Tests

```bash
sbt test
```

Los tests no requieren base de datos real: usan repositorios en memoria.

```
src/test/scala/com/portafolio/service/
├── AuthServiceSpec.scala
├── ProjectServiceSpec.scala
└── TechnologyServiceSpec.scala
```

## Migraciones

Flyway ejecuta las migraciones automáticamente al arrancar la aplicación.

```
src/main/resources/db/migration/
├── V1__create_users.sql
├── V2__create_media.sql
├── V3__create_technologies.sql
├── V4__create_projects.sql
└── V5__create_blog_posts.sql
```

## API — Resumen de endpoints

La referencia completa para el frontend está en [`API_CONTEXT.md`](./API_CONTEXT.md).

### Públicos (sin autenticación)

| Método | Ruta                         | Descripción                     |
|--------|------------------------------|---------------------------------|
| POST   | `/api/v1/auth/login`         | Obtener token JWT               |
| GET    | `/api/v1/technologies`       | Catálogo de tecnologías         |
| GET    | `/api/v1/projects`           | Proyectos publicados            |
| GET    | `/api/v1/projects/:slug`     | Proyecto por slug               |
| GET    | `/api/v1/blog`               | Posts publicados (paginado)     |
| GET    | `/api/v1/blog/:slug`         | Post por slug                   |

### Admin (requieren `Authorization: Bearer <token>`)

| Método | Ruta                                   | Descripción                         |
|--------|----------------------------------------|-------------------------------------|
| GET    | `/api/v1/admin/projects`               | Todos los proyectos (con borradores)|
| POST   | `/api/v1/admin/projects`               | Crear proyecto                      |
| PUT    | `/api/v1/admin/projects/reorder`       | Reordenar (drag & drop)             |
| PUT    | `/api/v1/admin/projects/:id`           | Actualizar proyecto                 |
| DELETE | `/api/v1/admin/projects/:id`           | Eliminar proyecto                   |
| GET    | `/api/v1/admin/blog`                   | Todos los posts (con borradores)    |
| POST   | `/api/v1/admin/blog`                   | Crear post                          |
| PUT    | `/api/v1/admin/blog/:id`               | Actualizar post                     |
| DELETE | `/api/v1/admin/blog/:id`               | Eliminar post                       |
| POST   | `/api/v1/admin/media/presign`          | Solicitar URL de subida             |
| POST   | `/api/v1/admin/media/confirm`          | Confirmar subida                    |
| GET    | `/api/v1/admin/media`                  | Listar archivos (paginado)          |
| GET    | `/api/v1/admin/media/:id`              | Obtener archivo por ID              |
| DELETE | `/api/v1/admin/media/:id`              | Eliminar archivo                    |
| POST   | `/api/v1/admin/technologies`           | Crear tecnología                    |
| PUT    | `/api/v1/admin/technologies/:id`       | Actualizar tecnología               |
| DELETE | `/api/v1/admin/technologies/:id`       | Eliminar tecnología                 |

### WebSocket

| Protocolo | Ruta                         | Descripción                          |
|-----------|------------------------------|--------------------------------------|
| WS        | `/ws/preview?token=<jwt>`    | Previsualización en tiempo real      |

## Decisiones de diseño

- **Opaque types para IDs**: `UserId`, `ProjectId`, etc. son tipos opacos sobre `UUID`. Previenen mezclas accidentales de IDs distintos en tiempo de compilación.
- **Almacenamiento pluggable**: el trait `StorageService` permite intercambiar S3 por almacenamiento local sin cambiar la lógica de negocio.
- **AWS vars opcionales en desarrollo**: con `APP_ENV=development` (default) no se requieren credenciales de AWS, lo que simplifica el setup local.
- **Sin efectos en constructores**: toda la inicialización es efectful y se gestiona en `Resource[IO, _]` para garantizar liberación correcta de recursos.
- **Tapir + Swagger generado**: los endpoints Tapir generan la documentación OpenAPI automáticamente. La UI está en `/`.
