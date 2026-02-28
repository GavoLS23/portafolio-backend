package com.portafolio.repository

import cats.effect.IO
import cats.syntax.all.*
import com.portafolio.domain.blog.*
import com.portafolio.domain.common.Ids.{BlogPostId, MediaId}
import com.portafolio.domain.common.Language
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.time.Instant
import java.util.UUID

/** Repositorio de blog posts con traducciones y tags. */
trait BlogRepository:
  def findAll(onlyPublished: Boolean, limit: Int, offset: Int): IO[List[BlogPost]]
  def countAll(onlyPublished: Boolean): IO[Long]
  def findById(id: BlogPostId): IO[Option[BlogPost]]
  def findBySlug(slug: String): IO[Option[BlogPost]]
  def findTranslations(postId: BlogPostId): IO[List[BlogPostTranslation]]
  def findTags(postId: BlogPostId): IO[List[String]]
  def create(req: CreateBlogPostRequest): IO[BlogPost]
  def update(id: BlogPostId, req: UpdateBlogPostRequest): IO[Option[BlogPost]]
  def delete(id: BlogPostId): IO[Boolean]
  def slugExists(slug: String, excludeId: Option[BlogPostId] = None): IO[Boolean]

object BlogRepository:

  def make(xa: Transactor[IO]): BlogRepository = new BlogRepository:


    given Meta[PostStatus] = Meta[String].timap(s => PostStatus.fromString(s).getOrElse(PostStatus.Draft))(_.value)

    given Meta[Language] = Meta[String].timap(s => Language.fromCode(s).getOrElse(Language.Es))(_.code)

    private type PostRow = (UUID, String, String, Option[UUID], Option[Instant], Instant, Instant)

    private def toPost(row: PostRow): BlogPost =
      val (id, slug, status, thumb, pub, ca, ua) = row
      BlogPost(
        id = BlogPostId(id),
        slug = slug,
        status = PostStatus.fromString(status).getOrElse(PostStatus.Draft),
        thumbnailMediaId = thumb.map(MediaId(_)),
        publishedAt = pub,
        createdAt = ca,
        updatedAt = ua
      )

    private val selectPost =
      fr"""SELECT id, slug, status::text, thumbnail_media_id, published_at,
                  created_at, updated_at
           FROM blog_posts"""

    def findAll(onlyPublished: Boolean, limit: Int, offset: Int): IO[List[BlogPost]] =
      val cond = if onlyPublished then fr"WHERE status = 'published'" else fr""
      (selectPost ++ cond ++ fr"ORDER BY created_at DESC LIMIT $limit OFFSET $offset")
        .query[PostRow]
        .map(toPost)
        .to[List]
        .transact(xa)

    def countAll(onlyPublished: Boolean): IO[Long] =
      val cond = if onlyPublished then fr"WHERE status = 'published'" else fr""
      (fr"SELECT COUNT(*) FROM blog_posts" ++ cond)
        .query[Long]
        .unique
        .transact(xa)

    def findById(id: BlogPostId): IO[Option[BlogPost]] =
      (selectPost ++ fr"WHERE id = ${id.value}")
        .query[PostRow]
        .map(toPost)
        .option
        .transact(xa)

    def findBySlug(slug: String): IO[Option[BlogPost]] =
      (selectPost ++ fr"WHERE slug = $slug")
        .query[PostRow]
        .map(toPost)
        .option
        .transact(xa)

    def findTranslations(postId: BlogPostId): IO[List[BlogPostTranslation]] =
      sql"""SELECT blog_post_id, language, title, excerpt, content
            FROM blog_post_translations WHERE blog_post_id = ${postId.value}"""
        .query[(UUID, String, String, String, String)]
        .map { case (pid, lang, title, excerpt, content) =>
          BlogPostTranslation(
            blogPostId = BlogPostId(pid),
            language = Language.fromCode(lang).getOrElse(Language.Es),
            title = title,
            excerpt = excerpt,
            content = content
          )
        }
        .to[List]
        .transact(xa)

    def findTags(postId: BlogPostId): IO[List[String]] =
      sql"SELECT tag FROM blog_post_tags WHERE blog_post_id = ${postId.value}"
        .query[String]
        .to[List]
        .transact(xa)

    def create(req: CreateBlogPostRequest): IO[BlogPost] =
      (for
        post <- sql"""
          INSERT INTO blog_posts (slug)
          VALUES (${req.slug})
          RETURNING id, slug, status::text, thumbnail_media_id,
                    published_at, created_at, updated_at
        """
          .query[PostRow]
          .map(toPost)
          .unique
        _ <- upsertTranslations(post.id, req.translations)
        _ <- setTags(post.id, req.tags)
      yield post).transact(xa)

    def update(id: BlogPostId, req: UpdateBlogPostRequest): IO[Option[BlogPost]] =
      (for
        updated <- updateFields(id, req)
        _ <- updated.traverse_ { p =>
          req.translations.traverse_(ts => upsertTranslations(p.id, ts)) *>
            req.tags.traverse_(tags => setTags(p.id, tags))
        }
      yield updated).transact(xa)

    def delete(id: BlogPostId): IO[Boolean] =
      sql"DELETE FROM blog_posts WHERE id = ${id.value}".update.run.map(_ > 0).transact(xa)

    def slugExists(slug: String, excludeId: Option[BlogPostId]): IO[Boolean] =
      excludeId match
        case None =>
          sql"SELECT EXISTS(SELECT 1 FROM blog_posts WHERE slug = $slug)"
            .query[Boolean]
            .unique
            .transact(xa)
        case Some(eid) =>
          sql"SELECT EXISTS(SELECT 1 FROM blog_posts WHERE slug = $slug AND id <> ${eid.value})"
            .query[Boolean]
            .unique
            .transact(xa)

    private def upsertTranslations(postId: BlogPostId, ts: List[BlogTranslationInput]): ConnectionIO[Unit] =
      sql"DELETE FROM blog_post_translations WHERE blog_post_id = ${postId.value}".update.run *>
        ts.traverse_ { t =>
          sql"""
            INSERT INTO blog_post_translations (blog_post_id, language, title, excerpt, content)
            VALUES (${postId.value}, ${t.language.code}, ${t.title}, ${t.excerpt}, ${t.content})
          """.update.run
        }

    private def setTags(postId: BlogPostId, tags: List[String]): ConnectionIO[Unit] =
      sql"DELETE FROM blog_post_tags WHERE blog_post_id = ${postId.value}".update.run *>
        tags.traverse_ { tag =>
          sql"INSERT INTO blog_post_tags (blog_post_id, tag) VALUES (${postId.value}, $tag)".update.run
        }

    private def updateFields(id: BlogPostId, req: UpdateBlogPostRequest): ConnectionIO[Option[BlogPost]] =
      val sets = List(
        req.slug.map(v => fr"slug = $v"),
        req.status.map(v => fr"status = ${v.value}::content_status"),
        req.thumbnailMediaId.map(v => fr"thumbnail_media_id = ${v.value}"),
        req.status.collect { case PostStatus.Published =>
          fr"published_at = NOW()"
        }
      ).flatten

      if sets.isEmpty then
        (selectPost ++ fr"WHERE id = ${id.value}")
          .query[PostRow]
          .map(toPost)
          .option
      else
        val setClause = sets.reduce(_ ++ fr", " ++ _)
        (fr"UPDATE blog_posts SET " ++ setClause ++
          fr"WHERE id = ${id.value}" ++
          fr"""RETURNING id, slug, status::text, thumbnail_media_id,
                        published_at, created_at, updated_at""")
          .query[PostRow]
          .map(toPost)
          .option
