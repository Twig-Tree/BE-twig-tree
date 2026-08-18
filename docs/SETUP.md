# 백엔드 로컬 실행 가이드

Docker만 설치돼 있으면 됩니다. Java/Gradle 설치는 필요 없습니다.
(아래 **방법 B**로 앱을 직접 실행할 때만 Java 17이 추가로 필요합니다. 백엔드 개발자용입니다.)

## 1. 사전 준비

- **Docker Desktop** 설치 후 실행 상태 유지 ([다운로드](https://www.docker.com/products/docker-desktop/))
- 저장소 클론

```bash
git clone <저장소 URL>
cd BE-twig-tree
git checkout develop
```

## 2. 환경변수 파일 만들기

프로젝트 루트(`compose.yaml`이 있는 위치)에서 템플릿을 복사합니다.

```bash
cp .env.example .env
```

그다음 아래 4개 값을 채웁니다. 나머지 항목은 기본값 그대로 두면 됩니다.
**실제 값은 백엔드 팀원에게 받아주세요. 이 파일은 절대 커밋하지 마세요(.gitignore에 이미 등록됨).**

```env
DB_PASSWORD=<백엔드팀에게 받기>
JWT_SECRET=<백엔드팀에게 받기>
GOOGLE_CLIENT_IDS=<백엔드팀에게 받기>
OPENAI_API_KEY=<백엔드팀에게 받기>
```

## 3. 실행

방법이 두 가지입니다. 프론트 개발자나 서버만 띄우면 되는 분은 **방법 A**만 보시면 됩니다.

### 방법 A. 전부 Docker로 (기본)

```bash
docker compose up --build
```

아래 3개 컨테이너가 순서대로 뜹니다.

| 서비스 | 이미지 | 포트 |
|---|---|---|
| db | postgres:16 | 5432 |
| redis | redis:7-alpine | 6379 |
| app | Spring Boot (자체 빌드) | **8080** |

- 첫 실행은 Gradle 빌드 때문에 몇 분 걸립니다. 이후엔 캐시로 빨라집니다.
- DB 테이블은 Flyway 마이그레이션으로 자동 생성되므로 따로 만질 것 없습니다.

### 방법 B. DB/Redis만 Docker, 앱은 직접 실행 (백엔드 개발자용)

앱 코드를 자주 고칠 때는 매번 이미지를 다시 빌드하는 게 느립니다. 이럴 땐 DB/Redis만 컨테이너로 띄우고 앱은 Gradle로 실행하세요. **Java 17**이 필요합니다.

```bash
docker compose up -d db redis
```

```bash
./gradlew bootRun
```

`build.gradle`이 루트의 `.env`를 자동으로 찾아 주입하므로, IDE 실행 설정에 환경변수를 손으로 넣을 필요가 없습니다.

- 이 방법은 `.env`에 `DB_URL`, `DB_USERNAME`까지 있어야 합니다. `.env.example`을 복사했다면 기본값 그대로 두면 됩니다.
- 내 로컬에서만 값을 바꾸고 싶으면 `.env.local`을 만드세요. `.env`보다 우선하고 커밋되지 않습니다.
- 셸 환경변수가 `.env`보다 항상 우선합니다. 한 번만 다르게 띄우려면 이렇게 쓰세요.
  ```bash
  DB_URL=jdbc:postgresql://localhost:5433/twigtree-db ./gradlew bootRun
  ```

## 4. 실행 확인

- Swagger(API 문서): http://localhost:8080/swagger-ui/index.html
- 여기서 전체 API 스펙 확인과 직접 호출 테스트가 가능합니다.

## 5. 프론트 개발 시 알아둘 것

- **CORS**: `http://localhost:3000` 만 허용됩니다(credentials 허용). 프론트 개발 서버를 3000 포트로 띄워주세요. 다른 포트가 필요하면 백엔드팀에 요청.
- **인증 방식**: JWT Bearer 토큰
  - 인증 없이 호출 가능: `POST /auth/google`, `POST /auth/refresh`, Swagger 경로
  - 그 외 모든 API는 `Authorization: Bearer <accessToken>` 헤더 필요
  - Access Token 유효기간 30분, Refresh Token 14일
- 로그인 흐름: 구글 로그인 → `POST /auth/google`로 구글 토큰 전달 → 서비스 access/refresh 토큰 발급

## 6. 종료 / 초기화

```bash
# 종료 (DB 데이터는 유지됨)
docker compose down

# 종료 + DB 데이터까지 완전 삭제 (다음 실행 시 새 DB로 시작)
docker compose down -v
```

## 자주 겪는 문제

- **포트 충돌 (5432 / 6379 / 8080 already in use)**: 로컬에 이미 PostgreSQL/Redis/다른 서버가 떠 있는 경우입니다. 해당 프로그램을 종료하고 다시 실행하세요.
- **app 컨테이너가 바로 죽음**: `.env`의 3개 값이 모두 채워져 있는지 확인하세요. 로그는 `docker compose logs app`으로 볼 수 있습니다.
- **코드가 바뀌었는데 반영이 안 됨**: 최신 코드를 pull 받은 뒤 `docker compose up --build`로 다시 빌드해야 합니다(`--build` 없이 up만 하면 이전 이미지 그대로 실행됨).
- **(방법 B) `Illegal base64 character` 등 엉뚱한 메시지로 부팅 실패**: 십중팔구 `.env`가 없거나 값이 비어 있는 경우입니다. 값이 비면 Spring이 `${JWT_SECRET}` 같은 문자열을 그대로 넘겨서 원인과 무관한 에러가 납니다. 실행 로그 맨 위의 `[env]` 경고 줄을 먼저 보세요. 어떤 키가 비었는지 찍어줍니다.
- **(방법 B) `Connection to localhost:5432 refused`**: DB 컨테이너가 안 떠 있는 경우입니다. `docker compose up -d db redis`로 먼저 띄우세요.
