-- ─────────────────────────────────────────────────────────────────────────────
-- V2: Tabla de media (imágenes y videos en S3)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TYPE media_type AS ENUM ('image', 'video');

CREATE TABLE media (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  s3_key      VARCHAR(1000) NOT NULL,
  s3_bucket   VARCHAR(255)  NOT NULL,
  filename    VARCHAR(500)  NOT NULL,
  mime_type   VARCHAR(100)  NOT NULL,
  media_type  media_type    NOT NULL,
  size_bytes  BIGINT        NOT NULL,
  width_px    INTEGER,          -- solo para imágenes
  height_px   INTEGER,          -- solo para imágenes
  duration_s  INTEGER,          -- solo para videos (segundos)
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX media_s3_key_idx ON media (s3_key);
CREATE INDEX media_created_at_idx ON media (created_at DESC);
