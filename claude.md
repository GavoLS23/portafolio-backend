# PROYECTO: PORTAFOLIO PERSONAL CON BACKEND PROPIO

## Descripción del Proyecto
Estoy construyendo un portafolio personal que consta de dos partes:
- Un panel de vista público donde se mostrarán mis proyectos (con imágenes, videos, descripciones y URLs de demo)
- Un panel de administrador privado donde yo podré gestionar todo el contenido

## Requisitos Funcionales

### Gestión de Contenido
- Las imágenes y videos deben subirse a AWS S3 (no al servidor local)
- Todo el contenido debe ser multilingüe: español e inglés
- Además de proyectos, el sistema debe incluir un blog
- Los proyectos deben poder reordenarse mediante drag & drop en el panel admin

### Panel de Administrador
- Debe permitir previsualización en vivo de los cambios mientras se edita
- Al subir imágenes, debe mostrarse una vista previa antes de confirmar la subida
- El sistema debe autoguardar los cambios automáticamente
- Solo yo tendré acceso (un único usuario administrador almacenado en base de datos)

### Consideraciones Técnicas
- No requiero SEO especial ni optimización para buscadores
- No necesito integración con analíticas (Google Analytics, etc.)
- El hosting debe ser gratuito o de muy bajo costo
- No hay fecha límite de entrega
- La prioridad absoluta es una arquitectura limpia y bien diseñada, no la velocidad de desarrollo

## Stack Tecnológico Definido

### Backend
- Scala 3 con programación funcional
- Http4s como framework HTTP
- Doobie para la capa de base de datos
- PostgreSQL como base de datos
- Circe para manejo de JSON
- JWT para autenticación
- Flyway para migraciones
- Cats Effect 3 para efectos funcionales
- Refined Types para validación en tiempo de compilación
- AWS SDK para Scala (integración con S3)
- WebSockets para funcionalidades en tiempo real (autoguardado, previsualización)

### Frontend
- Angular 17+ con Standalone Components
- Tailwind CSS para estilos
- Signals para manejo de estado
- @angular/localize para internacionalización
- Reactive Forms para formularios
- TipTap como editor de texto enriquecido
- @angular/cdk/drag-drop para reordenar proyectos
- Upload directo a S3 con firma digital

### Infraestructura
- Docker y Docker Compose para contenerización
- GitHub Actions para CI/CD
- VPS gratuito (evaluar Oracle Cloud Always Free, VPSWala.org o créditos iniciales de AWS/GCP)
- AWS S3 en su capa gratuita para almacenamiento de archivos

## Modelo de Datos (Estructura Base)

El modelo debe soportar:
- Proyectos con traducciones (es/en)
- Blog posts con traducciones (es/en)
- Media (imágenes y videos) almacenados en S3 con metadatos
- Relaciones polimórficas para adjuntar media a proyectos o blog posts
- Tecnologías y tags con relaciones muchos a muchos
- Un único usuario administrador

## Restricciones de Arquitectura
- El backend debe ser puramente funcional (sin efectos secundarios en constructores)
- Usar newtypes o refined types para todos los IDs y tipos de valor
- Endpoints versionados (/api/v1/...)
- Tests obligatorios para toda la lógica de negocio
- Documentación del código

## Estado Actual
Proyecto en fase de planificación. Se necesita comenzar con la configuración inicial.

## Próximas Decisiones
- Evaluar opciones concretas de VPS gratuito
- Definir si usar WebSockets o Server-Sent Events para tiempo real
- Determinar si escalar imágenes automáticamente o usar las originales
