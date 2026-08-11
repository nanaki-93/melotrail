# Ktor -> Spring Boot migration

The `server` module has been migrated from Ktor/Netty to Spring Boot 3.5.16 with Spring MVC.

## Preserved API surface

- `GET /health`
- Project CRUD and track endpoints under `/api/projects`
- `/api/config`
- Audio upload/download/waveform/export under `/api/audio`
- Worker endpoints under `/api/worker`
- Worker progress via Server-Sent Events
- Existing `shared` and `worker-client` modules

## Main changes

- Ktor routing/plugins replaced with Spring MVC controllers.
- Ktor multipart parsing replaced with Spring's `MultipartFile`.
- Ktor SSE output streams replaced with `SseEmitter`.
- Services are managed by Spring dependency injection.
- CORS and static web resources are configured through Spring MVC.
- Error responses are centralized in `ApiExceptionHandler`.
- The shared Kotlin domain/audio code remains unchanged.

## Configuration

The following environment variables are supported:

- `SERVER_PORT`
- `SERVER_HOST`
- `WORKER_BASE_URL`
- `PROJECT_STORAGE_PATH`
- `AUDIO_STORAGE_PATH`

The Gradle project remains multi-module and the server is still Kotlin/JVM on Java 21.
