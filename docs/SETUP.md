# 백엔드 로컬 실행 가이드

Docker만 설치돼 있으면 됩니다. Java/Gradle 설치는 필요 없습니다.

## 1. 사전 준비

- **Docker Desktop** 설치 후 실행 상태 유지 ([다운로드](https://www.docker.com/products/docker-desktop/))
- 저장소 클론

```bash
git clone <저장소 URL>
cd BE-twig-tree
git checkout develop
```

## 2. 환경변수 파일 만들기

프로젝트 루트(`compose.yaml`이 있는 위치)에 `.env` 파일을 만들고 아래 4개 값을 채웁니다.
**실제 값은 백엔드 팀원에게 받아주세요. 이 파일은 절대 커밋하지 마세요(.gitignore에 이미 등록됨).**

```env
DB_PASSWORD=<백엔드팀에게 받기>
JWT_SECRET=<백엔드팀에게 받기>
GOOGLE_CLIENT_IDS=<백엔드팀에게 받기>
OPENAI_API_KEY=<백엔드팀에게 받기>
```

## 3. 실행

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
- **app 컨테이너가 바로 죽음**: `.env`의 4개 값이 모두 채워져 있는지 확인하세요. 로그는 `docker compose logs app`으로 볼 수 있습니다.
- **코드가 바뀌었는데 반영이 안 됨**: 최신 코드를 pull 받은 뒤 `docker compose up --build`로 다시 빌드해야 합니다(`--build` 없이 up만 하면 이전 이미지 그대로 실행됨).

## 배포

OCI 인스턴스 배포 절차는 [DEPLOY.md](DEPLOY.md)를 참고하세요. 로컬 실행과는 다른 compose 파일 조합을 사용합니다.
