-- ─────────────────────────────────────────────────────────────────────────────
-- V5: Blog posts con traducciones (es/en) y tags libres
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE blog_posts (
  id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
  slug               VARCHAR(200)   UNIQUE NOT NULL,
  status             content_status NOT NULL DEFAULT 'draft',
  thumbnail_media_id UUID           REFERENCES media(id) ON DELETE SET NULL,
  published_at       TIMESTAMPTZ,
  created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE blog_post_translations (
  blog_post_id UUID         NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
  language     CHAR(2)      NOT NULL CHECK (language IN ('es', 'en')),
  title        VARCHAR(500) NOT NULL,
  excerpt      TEXT         NOT NULL DEFAULT '',
  content      TEXT         NOT NULL DEFAULT '',
  PRIMARY KEY (blog_post_id, language)
);

CREATE TABLE blog_post_tags (
  blog_post_id UUID         NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
  tag          VARCHAR(100) NOT NULL,
  PRIMARY KEY  (blog_post_id, tag)
);

CREATE INDEX blog_posts_status_published_idx ON blog_posts (status, published_at DESC);
CREATE INDEX blog_post_tags_tag_idx ON blog_post_tags (tag);

CREATE TRIGGER blog_posts_updated_at
  BEFORE UPDATE ON blog_posts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
