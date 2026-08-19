# 배포 가이드 (OCI)

로컬 실행은 [SETUP.md](SETUP.md)를 참고하세요. 이 문서는 OCI 인스턴스 배포만 다룹니다.

## 1. compose 파일 구성

세 파일로 나뉘어 있고, **로컬과 배포가 서로 다른 조합**을 사용합니다.

| 파일 | 역할 | 언제 적용되나 |
|---|---|---|
| `compose.yaml` | 공통 정의 (db · redis · app) | 항상 |
| `compose.override.yaml` | 로컬 전용. db(5432) · redis(6379) 포트 개방 | `docker compose up` 시 **자동** 병합 |
| `compose.prod.yaml` | 배포 전용. 메모리 · 로깅 · 프로파일 | `-f` 로 명시할 때만 |

`-f` 를 하나라도 지정하면 `compose.override.yaml` 은 자동 병합되지 않습니다. 배포 명령이 항상 `-f compose.yaml -f compose.prod.yaml` 형태인 이유입니다.

> ⚠️ 서버에서 `docker compose up -d` 만 실행하면 프로덕션 설정이 빠진 채로 뜹니다. 반드시 `-f` 두 개를 붙이세요.

## 2. 서버 `.env`

프로젝트 루트에 아래 4개 키가 모두 있어야 app 이 기동됩니다. 하나라도 비면 컨테이너가 즉시 죽습니다.

```env
DB_PASSWORD=
JWT_SECRET=
GOOGLE_CLIENT_IDS=
OPENAI_API_KEY=
```

`OPENAI_MODEL` 은 생략 시 `gpt-4o-mini` 가 사용됩니다.

## 3. 배포

```bash
git pull origin develop
docker compose -f compose.yaml -f compose.prod.yaml up -d --build
docker compose -f compose.yaml -f compose.prod.yaml logs -f app
```

- 첫 빌드는 의존성 다운로드 때문에 10분 안팎 걸립니다. 이후 재배포는 레이어 캐시로 짧아집니다.
- DB 스키마는 Flyway 가 자동 적용합니다. 로그에서 `Successfully applied N migrations` 를 확인하세요.
- OCI 시큐리티 리스트(VCN)에서 8080 인그레스가 열려 있어야 외부 호출이 됩니다. db · redis 포트는 **열지 마세요.**

## 4. 리소스 배분

인스턴스가 1GB(VM.Standard.E2.1.Micro)라 컨테이너 총합을 800m 로 묶고 OS · dockerd 몫 약 200m 를 남깁니다. 아래 수치는 추정이 아니라 프로덕션 조합을 실제로 띄워 측정한 값입니다.

| 서비스 | mem_limit | 유휴 실측 | 비고 |
|---|---|---|---|
| db | 176m | 33MiB | `shared_buffers=48MB`, `max_connections=20` (HikariCP 기본 풀 10) |
| redis | 48m | 5MiB | `maxmemory 24mb`, eviction 없음 (refresh token 유실 방지) |
| app | 576m | 472MiB | 힙 상한 256m 고정 |

### 힙은 비율로 주면 안 됩니다

`-XX:MaxRAMPercentage` 는 JVM 이 컨테이너 메모리 한도를 인식한다는 전제에서만 동작합니다. 실측 결과 이 환경에서는 인식이 실패해서, `mem_limit: 512m` 인 컨테이너 안에서 JVM 이 최대 힙을 **3.83G**(호스트 RAM 기준)로 잡았습니다. 그 결과 cgroup 한도를 2144회 치면서 247MB 가 스왑으로 밀려나 상시 스래싱 상태였습니다.

그래서 `-Xms128m -Xmx256m` 으로 절대값을 못 박았습니다. 인식 성공 여부와 무관하게 동일하게 동작하며, 이 설정에서 스왑 사용량과 한도 접촉 횟수가 모두 0 이 됐습니다.

메타스페이스에는 **상한을 두지 않았습니다.** 실측 커밋량이 91.6MB(NMT 기준)라 96m 로 잡았을 때 첫 요청 처리 중 `OutOfMemoryError: Metaspace` 로 요청 스레드가 죽었습니다. 총량은 `mem_limit` 이 막아주므로 굳이 이중으로 조일 필요가 없습니다.

### 알려진 한계

유휴 472MiB / 576m 이라 여유가 약 100MB 입니다. **10MB 문서(PDF · DOCX · HWP · HWPX) 파싱 시 POI · PDFBox 클래스가 추가로 로드되고 힙 사용도 올라가므로, 이 여유를 넘겨 스왑에 들어갈 수 있습니다.** 힙이 256m 로 묶여 있어 컨테이너가 OOM Kill 되기보다 요청 단위 `OutOfMemoryError` 로 실패할 가능성이 높습니다(컨테이너는 살아남음).

이 스택(Postgres + Redis + Spring Boot + 문서 파서)이 1GB 에서 편하게 돌아가는 구성은 아닙니다. 문서 파싱을 실제로 쓰기 시작하면 **OCI Always Free 의 Ampere A1 shape(4 OCPU / 24GB)으로 옮기는 것**이 근본적인 해결입니다. 같은 무료 한도이고, `Dockerfile` 이 소스에서 빌드하므로 ARM 에서도 그대로 동작합니다.

측정 방법:

```bash
docker stats --no-stream
docker exec <app컨테이너> sh -c 'grep -E "^VmRSS|^VmSwap" /proc/1/status'
docker exec <app컨테이너> cat /sys/fs/cgroup/memory.events   # max 가 늘어나면 한도를 치는 중
free -h
```

컨테이너 로그는 서비스당 10m × 3개로 로테이션됩니다. 부트 볼륨이 차오르는 것을 막기 위한 설정이니 함부로 늘리지 마세요.

## 5. 종료 / 롤백

```bash
# 종료 (DB 데이터 유지)
docker compose -f compose.yaml -f compose.prod.yaml down

# 이전 커밋으로 되돌리기
git checkout <이전_커밋> && docker compose -f compose.yaml -f compose.prod.yaml up -d --build
```

Flyway 마이그레이션은 코드를 되돌려도 **자동으로 롤백되지 않습니다.** 스키마 변경이 포함된 배포를 되돌릴 때는 DB 상태를 직접 확인해야 합니다.

## 자주 겪는 문제

- **app 이 `Migration ... failed` 로 죽음**: 기존 데이터가 새 제약을 위반하는 경우입니다. 예를 들어 V7 은 `nodes` · `workspaces` · `folders` 의 `name` 을 `VARCHAR(30)` 으로 줄이므로, 30자를 넘는 기존 행이 있으면 실패합니다. 운영 데이터가 아니라면 `down -v` 로 볼륨을 비우고 다시 올리는 편이 빠릅니다.
- **빌드 중 서버가 멈춘 것처럼 느려짐**: Gradle 빌드가 스왑을 쓰는 중입니다. `free -h` 로 스왑 사용량을 확인하고 기다리세요. 스왑이 없다면 먼저 잡아야 합니다.
- **app 이 healthy 인데 외부에서 접속 불가**: OCI 시큐리티 리스트의 8080 인그레스 규칙을 확인하세요. 인스턴스 내부 방화벽보다 이쪽이 원인인 경우가 많습니다.
