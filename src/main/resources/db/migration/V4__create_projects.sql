-- ─────────────────────────────────────────────────────────────────────────────
-- V4: Proyectos con traducciones (es/en), media y tecnologías
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TYPE content_status AS ENUM ('draft', 'published');

CREATE TABLE projects (
  id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
  slug                VARCHAR(200)   UNIQUE NOT NULL,
  status              content_status NOT NULL DEFAULT 'draft',
  display_order       INTEGER        NOT NULL DEFAULT 0,
  demo_url            VARCHAR(2000),
  repository_url      VARCHAR(2000),
  thumbnail_media_id  UUID           REFERENCES media(id) ON DELETE SET NULL,
  created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Traducciones por idioma (es / en)
CREATE TABLE project_translations (
  project_id       UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  language         CHAR(2)      NOT NULL CHECK (language IN ('es', 'en')),
  title            VARCHAR(500) NOT NULL,
  description      TEXT         NOT NULL DEFAULT '',
  long_description TEXT         NOT NULL DEFAULT '',
  PRIMARY KEY (project_id, language)
);

-- Relación muchos-a-muchos con tecnologías
CREATE TABLE project_technologies (
  project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  technology_id UUID NOT NULL REFERENCES technologies(id) ON DELETE CASCADE,
  PRIMARY KEY (project_id, technology_id)
);

-- Galería de media por proyecto (orden configurable)
CREATE TABLE project_media (
  project_id    UUID    NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  media_id      UUID    NOT NULL REFERENCES media(id) ON DELETE CASCADE,
  display_order INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (project_id, media_id)
);

CREATE INDEX projects_status_order_idx ON projects (status, display_order);

CREATE TRIGGER projects_updated_at
  BEFORE UPDATE ON projects
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
