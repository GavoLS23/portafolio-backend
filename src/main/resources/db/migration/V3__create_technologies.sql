-- ─────────────────────────────────────────────────────────────────────────────
-- V3: Tecnologías / tags que se asignan a proyectos
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE technologies (
  id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  name     VARCHAR(100) UNIQUE NOT NULL,
  icon_url VARCHAR(1000)
);
