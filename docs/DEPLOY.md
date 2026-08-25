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

프로젝트 루트에 템플릿을 복사해서 만듭니다.

```bash
cp .env.example .env
```

아래 4개 키가 모두 채워져 있어야 app 이 기동됩니다. 하나라도 비면 컨테이너가 즉시 죽습니다.

```env
DB_PASSWORD=
JWT_SECRET=
GOOGLE_CLIENT_IDS=
OPENAI_API_KEY=
```

`OPENAI_MODEL` 은 생략 시 `gpt-4o-mini` 가 사용됩니다.

`.env.example` 에 있는 `DB_URL` · `DB_USERNAME` 은 **배포에서는 쓰이지 않습니다.** 로컬에서 앱을
직접 실행할 때(`./gradlew bootRun`)만 필요한 값이고, 컨테이너에는 `compose.yaml` 이 DB 주소를
서비스명 `db` 로 직접 넣어주기 때문입니다. `.env` 의 `localhost` 값이 컨테이너에 새어 들어갈
일은 없으니 템플릿의 기본값 그대로 두면 됩니다.

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

인스턴스가 1GB(VM.Standard.E2.1.Micro)라 컨테이너 총합을 800m 로 묶습니다. 아래는 추정이 아니라 실제로 띄워 측정한 값이며, 실서버 열은 2026-08-19 배포 직후 워밍업(swagger · api-docs · 인증 필요 API 각 5회)을 마친 상태입니다.

| 서비스 | mem_limit | 실서버 실측 | 로컬 실측 | 비고 |
|---|---|---|---|---|
| db | 176m | 11MiB | 33MiB | `shared_buffers=48MB`, `max_connections=20` (HikariCP 기본 풀 10) |
| redis | 48m | 2MiB | 5MiB | `maxmemory 24mb`, eviction 없음 (refresh token 유실 방지) |
| app | 576m | 297MiB | 472MiB | 힙 상한 256m 고정 |

로컬(Docker Desktop / WSL2)이 더 크게 나오는 것은 cgroup 통계에 잡히는 범위와 워밍업 정도가 달라서입니다. 한도 산정은 큰 쪽인 로컬 값을 기준으로 잡았으므로 실서버에서는 여유가 더 있습니다.

### OS 몫을 200m 로 보면 안 됩니다

Ubuntu 이미지는 snapd · unattended-upgrades 등을 기본으로 돌려서 생각보다 무겁습니다. 실측:

```
Mem: 954Mi total, 690Mi used, 263Mi available    # 컨테이너 합계는 310MiB
```

즉 **OS · dockerd 가 약 390MiB** 를 씁니다. 컨테이너 한도 총합 800m 를 다 쓰면 1190m 가 되어 954Mi 를 넘기므로, 한도는 어디까지나 상한이지 목표치가 아닙니다. 한도를 올리기 전에 `free -h` 의 available 을 먼저 보세요.

메모리가 더 필요하면 컨테이너 한도를 올리기보다 OS 쪽을 덜어내는 편이 효과적입니다(1GB 머신에서 snapd 정리 등).

### 배포 전후 (2026-08-19)

6월 배포본은 `MaxRAMPercentage` 를 쓰고 있어서, 두 달 내내 스왑에 밀린 채 돌고 있었습니다.

| | 배포 전 | 배포 후(워밍업 완료) |
|---|---|---|
| 스왑 사용 | 464Mi | 350Mi |
| app | 한도 600m, 상시 스왑 | 297MiB / 576m |

워밍업 전(343Mi)과 후(350Mi)의 스왑이 사실상 같다는 점이 중요합니다. 요청을 처리하면서 새로 밀려난 것이 없다는 뜻입니다. 남은 350Mi 는 빌드 중 밀려난 시스템 프로세스 몫이라 해당 프로세스가 깨어나기 전까지 그대로 남습니다.

### 힙은 비율로 주면 안 됩니다

`-XX:MaxRAMPercentage` 는 JVM 이 컨테이너 메모리 한도를 인식한다는 전제에서만 동작합니다. 실측 결과 이 환경에서는 인식이 실패해서, `mem_limit: 512m` 인 컨테이너 안에서 JVM 이 최대 힙을 **3.83G**(호스트 RAM 기준)로 잡았습니다. 그 결과 cgroup 한도를 2144회 치면서 247MB 가 스왑으로 밀려나 상시 스래싱 상태였습니다.

그래서 `-Xms128m -Xmx256m` 으로 절대값을 못 박았습니다. 인식 성공 여부와 무관하게 동일하게 동작합니다. 로컬 검증에서는 이 설정으로 컨테이너 스왑과 한도 접촉이 모두 0 이 됐고, 실서버에서도 워밍업 중 스왑이 늘지 않았습니다(위 배포 전후 참고).

메타스페이스에는 **상한을 두지 않았습니다.** 실측 커밋량이 91.6MB(NMT 기준)라 96m 로 잡았을 때 첫 요청 처리 중 `OutOfMemoryError: Metaspace` 로 요청 스레드가 죽었습니다. 총량은 `mem_limit` 이 막아주므로 굳이 이중으로 조일 필요가 없습니다.

### 알려진 한계

실서버 기준 297MiB / 576m 로 한도까지 279MiB, 호스트 available 263Mi 가 남습니다. **10MB 문서(PDF · DOCX · HWP · HWPX) 파싱 시 POI · PDFBox 클래스가 추가로 로드되고 힙 사용도 올라가므로, 이 여유를 넘기면 스왑에 들어갑니다.** 힙이 256m 로 묶여 있어 컨테이너가 OOM Kill 되기보다 요청 단위 `OutOfMemoryError` 로 실패할 가능성이 높습니다(컨테이너는 살아남음).

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
