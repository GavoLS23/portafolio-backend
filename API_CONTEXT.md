# API Context — Portafolio Personal Backend

Referencia completa para consumir la API desde el frontend Angular.
La documentación interactiva (Swagger UI) está disponible en `http://localhost:8080/` cuando el servidor está corriendo.

---

## Configuración base

```
Base URL desarrollo : http://localhost:8080
Base URL producción : https://<tu-dominio>
Versión de la API   : /api/v1/...
Content-Type        : application/json
```

---

## Autenticación

La API usa **JWT Bearer tokens**. Los endpoints de administración requieren el header:

```
Authorization: Bearer <token>
```

### Obtener token

```
POST /api/v1/auth/login
```

**Request:**
```json
{
  "email": "admin@example.com",
  "password": "tu-contraseña"
}
```

**Response 200:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-03-01T00:00:00Z"
}
```

**Errores:** `401 Unauthorized` si las credenciales son incorrectas.

---

## Formato de errores

Todos los errores tienen la misma forma:

```json
{
  "error": "NotFound",
  "message": "Proyecto no encontrado: 550e8400-...",
  "statusCode": 404
}
```

| Campo        | Tipo   | Descripción                                          |
|--------------|--------|------------------------------------------------------|
| `error`      | string | Nombre de la clase del error (`NotFound`, `Unauthorized`, `Forbidden`, `BadRequest`, `Conflict`, `InternalError`) |
| `message`    | string | Descripción legible del problema                     |
| `statusCode` | number | Código HTTP correspondiente                          |

| `error`         | HTTP |
|-----------------|------|
| `NotFound`      | 404  |
| `Unauthorized`  | 401  |
| `Forbidden`     | 403  |
| `BadRequest`    | 400  |
| `Conflict`      | 409  |
| `InternalError` | 500  |

---

## Tipos compartidos

### Language
```
"es" | "en"
```

### ProjectStatus / PostStatus
```
"draft" | "published"
```

### MediaType
```
"image" | "video"
```

### IDs
Todos los IDs son **UUID v4** en formato string:
```
"550e8400-e29b-41d4-a716-446655440000"
```

---

## TypeScript — Interfaces

```typescript
// ── Compartidos ────────────────────────────────────────────────────────────

type Language = 'es' | 'en';
type ProjectStatus = 'draft' | 'published';
type PostStatus = 'draft' | 'published';
type MediaType = 'image' | 'video';

interface ErrorResponse {
  error: string;
  message: string;
  statusCode: number;
}

// ── Auth ──────────────────────────────────────────────────────────────────

interface LoginRequest {
  email: string;
  password: string;
}

interface LoginResponse {
  token: string;
  expiresAt: string; // ISO 8601
}

// ── Technologies ──────────────────────────────────────────────────────────

interface TechnologyResponse {
  id: string;
  name: string;
  iconUrl: string | null;
}

interface CreateTechnologyRequest {
  name: string;
  iconUrl?: string | null;
}

// ── Projects ──────────────────────────────────────────────────────────────

interface TranslationInput {
  language: Language;
  title: string;
  description: string;
  longDescription: string;
}

interface ProjectResponse {
  id: string;
  slug: string;
  status: ProjectStatus;
  displayOrder: number;
  demoUrl: string | null;
  repositoryUrl: string | null;
  thumbnailMediaId: string | null;
  translations: TranslationInput[];
  technologyIds: string[];
  createdAt: string; // ISO 8601
  updatedAt: string; // ISO 8601
}

interface CreateProjectRequest {
  slug: string;
  demoUrl?: string | null;
  repositoryUrl?: string | null;
  translations: TranslationInput[];
  technologyIds: string[];
}

interface UpdateProjectRequest {
  slug?: string;
  status?: ProjectStatus;
  demoUrl?: string | null;
  repositoryUrl?: string | null;
  thumbnailMediaId?: string | null;
  translations?: TranslationInput[];
  technologyIds?: string[];
}

interface ReorderRequest {
  orderedIds: string[];
}

// ── Blog ──────────────────────────────────────────────────────────────────

interface BlogTranslationInput {
  language: Language;
  title: string;
  excerpt: string;
  content: string; // HTML (TipTap)
}

interface BlogPostResponse {
  id: string;
  slug: string;
  status: PostStatus;
  thumbnailMediaId: string | null;
  publishedAt: string | null; // ISO 8601
  translations: BlogTranslationInput[];
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

interface CreateBlogPostRequest {
  slug: string;
  translations: BlogTranslationInput[];
  tags: string[];
}

interface UpdateBlogPostRequest {
  slug?: string;
  status?: PostStatus;
  thumbnailMediaId?: string | null;
  translations?: BlogTranslationInput[];
  tags?: string[];
}

// ── Media ─────────────────────────────────────────────────────────────────

interface MediaResponse {
  id: string;
  url: string;       // URL pública del archivo (S3 o servidor local en dev)
  filename: string;
  mimeType: string;
  mediaType: MediaType;
  sizeBytes: number;
  widthPx: number | null;
  heightPx: number | null;
  durationS: number | null;
  createdAt: string;
}

interface PresignedUploadRequest {
  filename: string;
  mimeType: string;
  mediaType: MediaType;
  sizeBytes: number;
}

interface PresignedUploadResponse {
  uploadUrl: string;   // URL donde el frontend debe hacer PUT
  mediaId: string;     // ID generado para este archivo
  s3Key: string;       // Clave del archivo en S3 (o ruta local en dev)
  expiresInS: number;  // Segundos hasta que caduca la URL de subida
}

interface ConfirmUploadRequest {
  mediaId: string;
  widthPx?: number | null;
  heightPx?: number | null;
  durationS?: number | null;
}
```

---

## Endpoints públicos

> No requieren `Authorization` header.

### Technologies

| Método | Ruta                         | Descripción                    |
|--------|------------------------------|--------------------------------|
| GET    | `/api/v1/technologies`       | Lista todas las tecnologías    |

**GET /api/v1/technologies**
```
Response 200: TechnologyResponse[]
```

### Projects

| Método | Ruta                         | Descripción                        |
|--------|------------------------------|------------------------------------|
| GET    | `/api/v1/projects`           | Lista proyectos publicados         |
| GET    | `/api/v1/projects/:slug`     | Obtiene un proyecto por slug       |

**GET /api/v1/projects**
```
Response 200: ProjectResponse[]
```

**GET /api/v1/projects/:slug**
```
Response 200: ProjectResponse
Response 404: ErrorResponse
```

### Blog

| Método | Ruta                         | Descripción                        |
|--------|------------------------------|------------------------------------|
| GET    | `/api/v1/blog`               | Lista posts publicados (paginado)  |
| GET    | `/api/v1/blog/:slug`         | Obtiene un post por slug           |

**GET /api/v1/blog**
```
Query params:
  page     : number  (default: 1)
  pageSize : number  (default: 20)

Response 200: BlogPostResponse[]
```

**GET /api/v1/blog/:slug**
```
Response 200: BlogPostResponse
Response 404: ErrorResponse
```

---

## Endpoints admin

> Requieren `Authorization: Bearer <token>` en todos los requests.

### Auth

| Método | Ruta                         | Descripción            |
|--------|------------------------------|------------------------|
| POST   | `/api/v1/auth/login`         | Obtener token JWT      |

### Technologies (admin)

| Método | Ruta                                     | Descripción               |
|--------|------------------------------------------|---------------------------|
| POST   | `/api/v1/admin/technologies`             | Crea una tecnología       |
| PUT    | `/api/v1/admin/technologies/:id`         | Actualiza una tecnología  |
| DELETE | `/api/v1/admin/technologies/:id`         | Elimina una tecnología    |

**POST /api/v1/admin/technologies**
```json
// Request: CreateTechnologyRequest
{ "name": "Scala", "iconUrl": "https://..." }

// Response 201: TechnologyResponse
```

**PUT /api/v1/admin/technologies/:id**
```
// Request: CreateTechnologyRequest
// Response 200: TechnologyResponse
// Response 404: ErrorResponse
```

**DELETE /api/v1/admin/technologies/:id**
```
// Response 204: (vacío)
// Response 404: ErrorResponse
```

### Projects (admin)

| Método | Ruta                                     | Descripción                        |
|--------|------------------------------------------|------------------------------------|
| GET    | `/api/v1/admin/projects`                 | Lista todos (incluyendo borradores)|
| POST   | `/api/v1/admin/projects`                 | Crea un proyecto                   |
| PUT    | `/api/v1/admin/projects/reorder`         | Reordena (drag & drop)             |
| PUT    | `/api/v1/admin/projects/:id`             | Actualiza un proyecto              |
| DELETE | `/api/v1/admin/projects/:id`             | Elimina un proyecto                |

> **Importante:** La ruta `/reorder` debe llamarse **antes** que `/:id` en el router del frontend para evitar que "reorder" sea interpretado como un ID.

**POST /api/v1/admin/projects**
```json
// Request: CreateProjectRequest
{
  "slug": "mi-proyecto",
  "demoUrl": "https://demo.example.com",
  "repositoryUrl": "https://github.com/...",
  "translations": [
    { "language": "es", "title": "Mi Proyecto", "description": "...", "longDescription": "..." },
    { "language": "en", "title": "My Project",  "description": "...", "longDescription": "..." }
  ],
  "technologyIds": ["550e8400-..."]
}
// Response 201: ProjectResponse
```

**PUT /api/v1/admin/projects/reorder**
```json
// Request: ReorderRequest
{ "orderedIds": ["id1", "id2", "id3"] }
// Response 204: (vacío)
```

**PUT /api/v1/admin/projects/:id**
```json
// Request: UpdateProjectRequest (todos los campos son opcionales)
{
  "status": "published",
  "thumbnailMediaId": "550e8400-..."
}
// Response 200: ProjectResponse
// Response 404: ErrorResponse
```

### Blog (admin)

| Método | Ruta                                     | Descripción                        |
|--------|------------------------------------------|------------------------------------|
| GET    | `/api/v1/admin/blog`                     | Lista todos (incluyendo borradores)|
| POST   | `/api/v1/admin/blog`                     | Crea un post                       |
| PUT    | `/api/v1/admin/blog/:id`                 | Actualiza un post                  |
| DELETE | `/api/v1/admin/blog/:id`                 | Elimina un post                    |

**GET /api/v1/admin/blog**
```
Query params:
  page     : number  (default: 1)
  pageSize : number  (default: 20)

Response 200: BlogPostResponse[]
```

**POST /api/v1/admin/blog**
```json
// Request: CreateBlogPostRequest
{
  "slug": "mi-primer-post",
  "translations": [
    { "language": "es", "title": "Hola mundo", "excerpt": "...", "content": "<p>...</p>" },
    { "language": "en", "title": "Hello world", "excerpt": "...", "content": "<p>...</p>" }
  ],
  "tags": ["scala", "backend"]
}
// Response 201: BlogPostResponse
```

### Media (admin)

| Método | Ruta                                     | Descripción                          |
|--------|------------------------------------------|--------------------------------------|
| POST   | `/api/v1/admin/media/presign`            | Solicita URL de subida               |
| POST   | `/api/v1/admin/media/confirm`            | Confirma subida y guarda metadata    |
| GET    | `/api/v1/admin/media`                    | Lista archivos (paginado)            |
| GET    | `/api/v1/admin/media/:id`                | Obtiene un archivo por ID            |
| DELETE | `/api/v1/admin/media/:id`                | Elimina archivo (BD + almacenamiento)|

---

## Flujo de subida de archivos

El flujo es idéntico en desarrollo y producción. La diferencia es invisible para el frontend:
en **desarrollo** la `uploadUrl` apunta al propio servidor; en **producción** apunta a S3.

```
1. POST /api/v1/admin/media/presign
   → obtiene uploadUrl + mediaId

2. PUT <uploadUrl>                    ← directo, sin Authorization header
   Body: binario del archivo
   Content-Type: <mimeType del archivo>

3. POST /api/v1/admin/media/confirm
   → guarda metadata en BD, obtiene MediaResponse con URL pública
```

**Paso 1 — Solicitar URL de subida:**
```typescript
// Request: PresignedUploadRequest
const presignReq = {
  filename: 'foto.jpg',
  mimeType: 'image/jpeg',
  mediaType: 'image',
  sizeBytes: file.size
};

// Response 201: PresignedUploadResponse
const { uploadUrl, mediaId, expiresInS } = await api.post('/api/v1/admin/media/presign', presignReq);
```

**Paso 2 — Subir el archivo directamente:**
```typescript
// PUT directo a uploadUrl — sin Authorization header
await fetch(uploadUrl, {
  method: 'PUT',
  body: file,
  headers: { 'Content-Type': file.type }
});
```

**Paso 3 — Confirmar la subida:**
```typescript
// Request: ConfirmUploadRequest
const confirmReq = {
  mediaId,
  widthPx: 1920,   // opcional, para imágenes
  heightPx: 1080,  // opcional, para imágenes
  durationS: null  // opcional, para videos
};

// Response 200: MediaResponse
const media = await api.post('/api/v1/admin/media/confirm', confirmReq);
// media.url → URL pública del archivo ya subido
```

**GET /api/v1/admin/media**
```
Query params:
  page     : number  (default: 1)
  pageSize : number  (default: 50)

Response 200: MediaResponse[]
```

---

## WebSocket — Previsualización en tiempo real

Endpoint: `ws://localhost:8080/ws/preview?token=<jwt>`

> El token va como query param porque los WebSockets del navegador no admiten headers personalizados en el handshake inicial.

### Conectar

```typescript
const ws = new WebSocket(`ws://localhost:8080/ws/preview?token=${jwtToken}`);
```

Si el token es inválido o ha expirado, el servidor responde con `403 Forbidden` y cierra la conexión.

El servidor envía un **ping cada 30 segundos** para mantener la conexión viva.

### Mensajes del servidor → cliente

**preview_update** — cuando hay un cambio en una entidad:
```json
{
  "type": "preview_update",
  "entityType": "project" | "blog_post",
  "entityId": "550e8400-...",
  "data": { /* objeto completo actualizado */ }
}
```

**autosave_ack** — confirmación de autoguardado:
```json
{
  "type": "autosave_ack",
  "entityId": "550e8400-...",
  "savedAt": "2026-02-28T00:00:00Z"
}
```

**error** — error del servidor:
```json
{
  "type": "error",
  "message": "descripción del error"
}
```

### Mensajes del cliente → servidor

**autosave** — solicitar guardado automático:
```json
{
  "type": "autosave",
  "entityId": "550e8400-...",
  "data": { /* datos a guardar */ }
}
```

---

## Rutas exclusivas de desarrollo

> Estas rutas **solo existen cuando `APP_ENV=development`** (default). No se montan en producción.

| Método | Ruta                                              | Descripción                    |
|--------|---------------------------------------------------|--------------------------------|
| PUT    | `/api/v1/dev/upload/{folder}/{uuid}/{filename}`   | Recibe el archivo binario      |
| GET    | `/api/v1/dev/files/{folder}/{uuid}/{filename}`    | Sirve el archivo guardado      |

Son usadas automáticamente por el flujo de media cuando `uploadUrl` apunta al servidor local. El frontend no necesita llamarlas directamente.

---

## Notas de implementación Angular

### Interceptor de autenticación

```typescript
// auth.interceptor.ts
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token();
  if (token && !req.url.includes('/auth/login')) {
    req = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${token}`)
    });
  }
  return next(req);
};
```

### Subida de archivos al paso 2 (upload directo)

```typescript
// El PUT a uploadUrl NO debe pasar por el interceptor de auth
// porque S3 rechaza headers de Authorization no firmados.
// Usar fetch() directamente o un HttpClient sin interceptores para esa llamada.
await fetch(presigned.uploadUrl, {
  method: 'PUT',
  body: file,
  headers: { 'Content-Type': file.type }
});
```

### Paginación

Los endpoints con paginación usan query params `page` (1-indexado) y `pageSize`.
La respuesta es un array directo (no hay wrapper con `total`). Para obtener el total,
usa el endpoint de lista admin que sí puede devolver todos los registros.

### Multilingüismo

Las `translations` en los responses son un array. Filtra por `language` para mostrar el idioma activo:
```typescript
const t = project.translations.find(t => t.language === currentLang);
```
