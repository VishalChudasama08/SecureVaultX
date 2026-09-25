# SecureVaultX

A secure, cross-platform encrypted file vault: a Flutter client talking
exclusively to a Spring Boot REST API, which is the only component allowed
to reach MySQL and the encrypted-file storage.

Every file is encrypted with **AES-256-GCM** using **envelope encryption**
(a random key per file, wrapped by an environment-configured master key),
streamed in constant memory regardless of file size, and never trusted as
"decrypted" until its authentication tag has been fully verified.

> Full architecture, cryptography, and database design rationale:
> **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**

---

## Table of contents

- [Features](#features)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Security model](#security-model)
- [Two storage modes](#two-storage-modes)
- [Project structure](#project-structure)
- [Prerequisites](#prerequisites)
- [MySQL setup](#mysql-setup)
- [Environment variables](#environment-variables)
- [Running the backend](#running-the-backend)
- [Running the Flutter app](#running-the-flutter-app)
- [Testing](#testing)
- [API overview](#api-overview)
- [Security notes](#security-notes)
- [Limitations](#limitations)
- [Future improvements](#future-improvements)

---

## Features

- Register / log in / log out with a secure, cookie-based session
  (Spring Security), CSRF-protected
- **Store in Secure Vault** — encrypt a file and keep it on the server
  (files ≤ 5 MB by default, configurable), then list / download / delete it
  later
- **Encrypt & Download** — encrypt any size of file and receive the `.enc`
  immediately; the server keeps no copy. Upload the `.enc` again later to
  decrypt it
- Works with arbitrary binary files — no extension/MIME restrictions
- Streaming upload/encryption/download — memory use stays roughly constant
  regardless of file size
- Tampered, truncated, or wrong-key `.enc` files are rejected before any
  plaintext is ever released
- Responsive Flutter UI: bottom navigation on phones, a navigation rail on
  tablets/desktops, light and dark themes

## Architecture

```text
Flutter (Android / Windows / desktop)
                    │
                    │ HTTPS / REST / JSON
                    ▼
             Spring Boot Backend
                    │
       ┌────────────┼────────────┐
       │            │            │
 Controller      Service      Security
       │            │            │
       └────────────┼────────────┘
                    │
                Repository
                    │
              JPA / Hibernate
                    │
                  MySQL                Local disk (encrypted files only)
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full reasoning
behind every major decision (why session auth instead of JWT, why CSRF
stays on, the exact `.enc` file format, the database schema, and how
failure/partial-write scenarios are handled).

## Technology stack

**Backend:** Java 21 · Spring Boot 4.1.0 · Spring Web MVC · Spring Data JPA
(Hibernate) · Spring Security (session auth, BCrypt) · Bean Validation ·
Flyway migrations · MySQL 8 · Maven

**Frontend:** Flutter (Dart) · `provider` (state) · `go_router` (routing) ·
`dio` + `dio_cookie_manager` + `cookie_jar` (HTTP, persisted session) ·
`file_picker` / `path_provider` (native file dialogs & storage)

**Testing:** JUnit 5, Spring's `MockMvc` + an in-memory H2 (MySQL-mode)
database running the real Flyway migrations, `flutter_test`

## Security model

| Concern | Approach |
|---|---|
| Passwords | BCrypt (cost 12) via Spring Security; never logged, never returned, never in exception messages |
| Authentication | Spring Security session (HttpOnly cookie); **no JWT** in this version (see [ARCHITECTURE.md §2](docs/ARCHITECTURE.md#2-why-session-auth-not-jwt)) |
| CSRF | Enabled (cookie-based auth requires it — see [ARCHITECTURE.md §3](docs/ARCHITECTURE.md#3-why-csrf-stays-enabled)); token obtained from `GET /api/auth/csrf` |
| File encryption | AES-256-GCM, envelope encryption, one random data key per file, streamed in fixed-size authenticated chunks |
| Master key | `SECUREVAULTX_MASTER_KEY`, environment-only, validated at startup, never logged, never silently regenerated |
| Authorization | Every file lookup is scoped to `owner = current session user` at the query level — another user's file id behaves exactly like a nonexistent one (404) |
| Filenames | Treated as untrusted display metadata only; storage uses server-generated UUIDs; sanitized before ever appearing in a response or a `Content-Disposition` header |
| Error responses | Centralized, RFC 9457 `problem+json`, with a stable `code` field for the client — never stack traces, SQL, paths, or key material |

## Two storage modes

**Store in Secure Vault** (≤ configured limit, default 5 MB) — the server
keeps the encrypted file; you can list, re-download (decrypted or still
encrypted), or delete it later.

**Encrypt & Download** (any size) — the server encrypts your file, streams
the `.enc` back, and keeps nothing. To decrypt later, upload that same
`.enc` file back to the app; it is authenticated and decrypted only after
the server confirms you own the corresponding record.

The backend enforces the size policy on bytes actually received, regardless
of what the Flutter UI shows — the frontend limit is a convenience, not the
security boundary.

## Project structure

```text
SecureVaultX/
├── securevaultx-spring-boot/      Spring Boot backend
│   ├── src/main/java/com/securevaultx/backend/
│   │   ├── config/                Spring Security, CORS, crypto & storage properties/wiring
│   │   ├── controllers/           REST controllers (thin — delegate to services)
│   │   ├── crypto/                Framework-free AES-256-GCM core (no Spring dependency at all)
│   │   ├── entities/              JPA entities
│   │   ├── exception/             ApiException + centralized @RestControllerAdvice
│   │   ├── repositories/          Spring Data JPA repositories
│   │   ├── request/ response/     DTOs (entities are never exposed through the API)
│   │   ├── security/              UserDetailsService, REST-friendly auth success/failure handlers
│   │   ├── services/              Business logic: AuthService, VaultService, TransferService, EnvelopeCryptoService
│   │   ├── storage/                FileStorage abstraction + local-disk implementation
│   │   └── util/                  FilenameSanitizer, EmailNormalizer, LimitedInputStream
│   └── src/main/resources/db/migration/   Flyway SQL migrations (reproducible schema)
│
├── SecureVaultX_UI/                Flutter frontend
│   └── lib/
│       ├── core/                  API client, error model, config, breakpoints, formatters
│       ├── models/                Plain data classes mirroring API DTOs
│       ├── services/              AuthService / VaultService — the only places that call the API client
│       ├── state/                 AuthController, TransferController (ChangeNotifier)
│       ├── routing/                go_router configuration + auth redirect guard
│       ├── screens/                One file per screen (login, register, home, encrypt, vault, decrypt)
│       ├── widgets/                Reusable components (password field, transfer progress card, dialogs…)
│       └── theme/                 Centralized Material 3 theme (light + dark)
│
├── docs/ARCHITECTURE.md            Full design rationale, ER diagram, encrypted-file format
├── .env.example                    Documented environment variables (no real secrets)
└── .gitignore
```

## Prerequisites

- JDK 21
- Maven (or use the included wrapper, `./mvnw`)
- MySQL 8.x running locally (or reachable) for the backend
- Flutter SDK (a recent stable channel release) for the frontend
- `openssl` (or any tool that can produce 32 random bytes, base64-encoded) to generate the master key

## MySQL setup

Flyway creates the schema automatically on first startup — you only need an
empty, reachable database:

```sql
CREATE DATABASE IF NOT EXISTS securevaultx
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

(`spring.datasource.url` also carries `createDatabaseIfNotExist=true`, so on
most local setups you can skip even that.) See
[docs/ARCHITECTURE.md §6](docs/ARCHITECTURE.md#6-database-schema) for the
schema itself and an ER diagram.

## Environment variables

Copy [`.env.example`](.env.example) and fill in real values (see the file
for a full explanation of each one). At minimum:

```bash
export SECUREVAULTX_DB_USER=root
export SECUREVAULTX_DB_PASSWORD=your-mysql-password
export SECUREVAULTX_MASTER_KEY=$(openssl rand -base64 32)
```

**The master key is required.** The application deliberately refuses to
start without a valid one — see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for why a missing/invalid key
is treated as a startup failure rather than something to silently work
around. **Back this value up somewhere safe once you generate it for a real
environment: losing it makes every previously stored/encrypted file
permanently undecryptable.**

## Running the backend

```bash
cd securevaultx-spring-boot
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080` by default
(`SECUREVAULTX_PORT` to change it). Sanity check:

```bash
curl http://localhost:8080/api/files/policy
# → 401 UNAUTHENTICATED (expected — this endpoint requires a session)
curl http://localhost:8080/api/auth/csrf
# → { "headerName": "X-CSRF-TOKEN", "token": "…" }
```

## Running the Flutter app

```bash
cd SecureVaultX_UI
flutter pub get
```

By default the app points at `http://localhost:8080`. To target a
different backend (for example the Android emulator's host-machine
alias, `10.0.2.2`):

```bash
flutter run --dart-define=SVX_API_BASE_URL=http://10.0.2.2:8080
```

Then run it for your target platform, e.g.:

```bash
flutter run -d windows      # Windows desktop
flutter run -d <device-id>  # Android device/emulator — see `flutter devices`
```

> This repository ships only `lib/`, `pubspec.yaml`, and Dart tests — the
> platform scaffolding (`android/`, `windows/`, etc.) is generated, not
> hand-edited, so it isn't checked in. Run `flutter create .` once inside
> `SecureVaultX_UI/` (safe — it only adds the platform folders for
> whichever platforms your installed Flutter SDK supports; it will not
> overwrite `lib/`) before your first `flutter run` for a given platform.

## Testing

**Backend:**

```bash
cd securevaultx-spring-boot
./mvnw clean test        # unit + MockMvc integration tests (H2, no Docker/MySQL needed)
./mvnw clean package      # full build, produces target/*.jar
```

Test coverage includes: encryption round-trip correctness, tamper/wrong-key/
truncation rejection, streaming a 48 MB file, the format-v1 byte-for-byte
contract (a golden vector, so a future refactor can't silently break
existing `.enc` files), registration/login/logout, CSRF enforcement,
protected-endpoint rejection, the 5 MB vault boundary (4.999 MB / exactly
5 MB / 5 MB + 1 byte), and — critically — that user A gets a plain 404 (not
403) for user B's files, metadata, downloads, and deletes.

**Frontend:**

```bash
cd SecureVaultX_UI
flutter analyze
flutter test
```

Covers: the HTTP error-mapping layer, `Content-Disposition` filename
parsing, the auth state machine (bootstrap/login/register/logout/session
expiry), and widget tests for the login/register/vault screens including
the responsive navigation switch between a bottom bar and a navigation
rail.

> **A note on this delivery:** the sandbox this project was built in has no
> outbound network access to Maven Central or pub.dev, and no Flutter SDK
> installed, so `./mvnw test` and `flutter test` could not be executed
> *here*. What **was** verified directly, in this environment: the entire
> AES-256-GCM/envelope-encryption/streaming core (`crypto/`, `storage/`,
> `util/`) compiles standalone with `javac` against the real JDK and passes
> all 57 JUnit tests, and its output was independently re-decrypted with a
> second, unrelated implementation (Python's `cryptography` library) to
> confirm the on-disk format is correct and self-describing. The Spring
> (`controllers/services/security`) and Flutter layers were reviewed line
> by line against the current Spring Boot 4.1.0 and Flutter APIs but not
> executed; please run the two test commands above after cloning.

## API overview

All endpoints are under `/api`. Session cookie required on everything
except registration and CSRF; CSRF token (`X-CSRF-TOKEN` header) required
on every `POST`/`DELETE`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/auth/csrf` | Get a CSRF token for the current session |
| POST | `/api/auth/register` | Create an account |
| POST | `/api/auth/login` | Log in (form-encoded `email` + `password`) |
| POST | `/api/auth/logout` | Invalidate the session |
| GET | `/api/auth/me` | Current user's profile |
| GET | `/api/files/policy` | Vault size limit + max upload size, for the UI |
| POST | `/api/files/encrypt` | Encrypt & Download — body: raw file bytes |
| POST | `/api/files/decrypt` | Decrypt an uploaded `.enc` — body: raw file bytes |
| POST | `/api/vault/files` | Store in Secure Vault — body: raw file bytes |
| GET | `/api/vault/files` | List your vault files |
| GET | `/api/vault/files/{id}` | Metadata for one vault file |
| GET | `/api/vault/files/{id}/content` | Download, decrypted |
| GET | `/api/vault/files/{id}/encrypted` | Download the stored `.enc` as-is |
| DELETE | `/api/vault/files/{id}` | Delete a vault file |

File uploads are sent as a raw `application/octet-stream` body (not
multipart) with the original filename percent-encoded in an `X-Filename`
header — this keeps the whole request body a single byte stream that flows
straight into the cipher, with nothing spooled or buffered by a multipart
parser first.

## Security notes

- Changing or losing `SECUREVAULTX_MASTER_KEY` makes every existing wrapped
  data key — and therefore every previously encrypted file — permanently
  undecryptable. There is no recovery path; this is inherent to how
  envelope encryption is supposed to work, not a bug.
- This is **server-side** encryption, not end-to-end: the plaintext passes
  through the backend process while it is being encrypted or decrypted.
  Only run this against a backend and database you trust, over HTTPS in
  any real deployment.
- The encrypted-storage directory (`SECUREVAULTX_STORAGE_ROOT`) must never
  be exposed as a static web resource — the application never configures
  it as one, and it should not be placed under any web server's public
  document root.

## Limitations

- No password reset / email verification flow (no email sending in scope)
- No admin dashboard, roles, or sharing between users
- Master key rotation is not automated (the schema stores `wrap_key_id` per
  record so a rotation job has what it needs, but the job itself is future
  work)
- Local disk storage only — no cloud object storage integration
- Session-only auth — no API tokens for machine-to-machine use

## Future improvements

- Optional JWT-based auth for non-cookie clients (additive — see
  [docs/ARCHITECTURE.md §2](docs/ARCHITECTURE.md#2-why-session-auth-not-jwt))
- Cloud KMS-backed `MasterKeyProvider` implementation (AWS KMS / Azure Key
  Vault / GCP KMS / HashiCorp Vault) behind the existing abstraction
- Master key rotation job (re-wrap every `EncryptionRecord`'s data key
  under a new `wrap_key_id` without re-encrypting file bodies)
- OpenAPI/Swagger documentation generation
- Flutter Web support (the backend's CORS configuration already supports
  this; it was left disabled by default per the spec's Android/Windows
  focus)
