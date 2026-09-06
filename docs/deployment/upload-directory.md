# Upload directory

Pet-Lee stores uploaded photographs as files on the server's disk and keeps only a public URL path
in the database. `com.petlee.config.StorageConfig` decides where those files go; T-23 serves them
back at `/images/<filename>`.

## Where it must not be

**Never inside the deployment.** A Jakarta EE server explodes a WAR into a work directory and
deletes that directory on every redeploy. An upload root inside it means each redeploy silently
destroys the users' photographs — and nothing reports it, because the `pet_image` rows survive and
every image simply 404s afterwards.

## Resolution order

`StorageConfig` takes the first of these that is set:

| # | Source | Example |
|---|---|---|
| 1 | System property `petlee.upload.dir` | `-Dpetlee.upload.dir=D:\petlee-uploads` |
| 2 | Environment variable `PETLEE_UPLOAD_DIR` | `PETLEE_UPLOAD_DIR=/var/lib/petlee-uploads` |
| 3 | A `petlee-uploads` directory **beside** the server's domain directory | see below |
| 4 | `petlee-uploads` under the user's home directory | a plain JVM, e.g. a unit test |

Row 3 reads whichever of `com.sun.aas.instanceRoot` (Payara 6, GlassFish 7),
`jboss.server.base.dir` (WildFly) or `catalina.base` (Tomcat) the server sets. Payara's
`instanceRoot` is `.../payara6/glassfish/domains/domain1`, so the default lands at
`.../payara6/glassfish/domains/petlee-uploads` — beside the domain, not inside it, because a
domain directory belongs to the server and is recreated whole.

The directory is created at start-up, not at the first upload, so a misconfigured or unwritable
path fails at deployment where somebody is watching.

> **Why not `application.properties`?** T-16's Definition of Done named one, but the project has no
> properties-file mechanism and ADR-003 keeps the dependency list closed, so nothing would read it.
> A file that documents a setting no code loads is worse than no file. This page and T-42's runbook
> are the documentation instead.

## Setting it

**Payara 6** — as a JVM option on the domain:

```
asadmin create-jvm-options "-Dpetlee.upload.dir=D:\\petlee-uploads"
asadmin restart-domain
```

**WildFly 31** — in `standalone.xml`, or on the command line:

```
standalone.bat -Dpetlee.upload.dir=D:\petlee-uploads
```

**Any server, through the environment** — set `PETLEE_UPLOAD_DIR` before starting it.

## What lands there

Filenames are generated, never taken from the upload: `<petId>_<UUID>.<ext>`, where the extension
comes from the file's *detected* type. No part of the client's filename survives, which is what
makes path traversal, embedded null bytes and unicode filename tricks irrelevant in one move. The
database stores `/images/<filename>` — a URL path, never a filesystem path, so the server's
directory layout does not leak into the JSON contract.

Limits: 5 MB per file, 8 files per pet, and only JPEG, PNG, WebP and GIF, decided from the file's
leading bytes rather than the client's `Content-Type` header.

## Backup

These files are the only copy. They are not in the WAR, not in the database, and not recreated by
`schema.sql`. Whatever backs up `petlee` should back up this directory too.
