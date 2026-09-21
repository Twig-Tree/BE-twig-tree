# 배포 가이드 (OCI)

로컬 실행은 [SETUP.md](SETUP.md)를 참고하세요. 이 문서는 OCI 인스턴스 배포만 다룹니다.

## 1. compose 파일 구성

세 파일로 나뉘어 있고, **로컬과 배포가 서로 다른 조합**을 사용합니다.

| 파일 | 역할 | 언제 적용되나 |
|---|---|---|
| `compose.yaml` | 공통 정의 (db · redis · app) | 항상 |
| `compose.override.yaml` | 로컬 전용. app(8080) · db(5432) · redis(6379) 포트 개방 | `docker compose up` 시 **자동** 병합 |
| `compose.prod.yaml` | 배포 전용. Caddy(80/443) · 메모리 · 로깅 · 프로파일 | `-f` 로 명시할 때만 |

`-f` 를 하나라도 지정하면 `compose.override.yaml` 은 자동 병합되지 않습니다. 배포 명령이 항상 `-f compose.yaml -f compose.prod.yaml` 형태인 이유입니다.

> ⚠️ 서버에서 `docker compose up -d` 만 실행하면 프로덕션 설정이 빠진 채로 뜹니다. 반드시 `-f` 두 개를 붙이세요.

## 2. 서버 `.env`

최초 배포 시 프로젝트 루트에 템플릿을 복사해서 만듭니다. 기존 서버의 `.env`는 그대로 유지합니다.

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

### HTTPS 구성과 사전 조건

외부 요청은 `https://api.twig-tree.com` → Caddy → Docker 내부 `app:8080` 순서로 전달됩니다.
`deploy/caddy/Caddyfile`에 도메인과 프록시 대상을 지정하며, 인증서 발급·갱신과 HTTP → HTTPS 전환은 Caddy가 처리합니다.
운영 Spring은 `server.forward-headers-strategy: native`로 전달 헤더를 처리합니다.

- DNS의 `api` A 레코드는 OCI 공인 IPv4를 가리켜야 합니다. AAAA 레코드가 있다면 IPv6 경로도 정상이어야 합니다.
- OCI 보안 목록/NSG와 서버 방화벽에서 TCP 80·443 접근을 허용합니다. 인증서 발급·갱신에 필요한 외부 DNS 및 HTTPS 통신도 가능해야 합니다.
- 서버의 80·443 포트를 다른 프로세스가 사용하지 않아야 합니다. UFW 설치는 필수 조건이 아닙니다.
- 운영 Compose는 앱의 8080 포트를 호스트에 공개하지 않습니다. DB·Redis도 외부에 공개하지 않습니다.
- 인증서 상태는 `caddy_data`, 설정 상태는 `caddy_config` 볼륨에 유지됩니다. 갱신을 위해 Caddy와 80·443 접근을 유지합니다.

### 소스 준비

아래 명령은 변경 사항이 원격 브랜치에 올라간 후, 서버의 저장소 디렉터리에서 실행합니다.
`git status --short`에 변경 파일이 나오면 먼저 내용을 확인하고 보존합니다.
배포 전 커밋은 롤백을 위해 별도로 기록합니다.

```bash
git status --short
git rev-parse HEAD
git fetch origin
git switch 'feat/#42-https'
git pull --ff-only origin 'feat/#42-https'
```

이 브랜치가 `develop`에 병합된 뒤에는 위 명령의 브랜치명을 `develop`으로 바꿉니다.

### 검증 및 적용

각 명령이 성공한 것을 확인한 뒤 다음 명령을 실행합니다. `config --quiet`는 `.env` 값을 출력하지 않고 설정을 검증합니다.

```bash
docker compose -f compose.yaml -f compose.prod.yaml config --quiet
docker compose -f compose.yaml -f compose.prod.yaml pull caddy
docker compose -f compose.yaml -f compose.prod.yaml run --rm --no-deps caddy \
  caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose -f compose.yaml -f compose.prod.yaml build app
docker compose -f compose.yaml -f compose.prod.yaml up -d
docker compose -f compose.yaml -f compose.prod.yaml ps
docker compose -f compose.yaml -f compose.prod.yaml logs --tail=100 caddy app
```

- 앱 재생성 시 짧은 중단이 발생합니다. Caddy의 `service_started`는 Spring 준비 완료를 보장하지 않으므로 기동 중 잠시 502가 나올 수 있습니다.
- 첫 빌드는 의존성 다운로드 때문에 10분 안팎 걸립니다. 이후 재배포는 레이어 캐시로 짧아집니다.
- DB 스키마는 Flyway 가 자동 적용합니다. 로그에서 `Successfully applied N migrations` 를 확인하세요.

### 동작 확인

앱 기동과 인증서 발급 후 실행합니다. HTTPS 검증에는 인증서 검증을 생략하는 `-k` 옵션을 사용하지 않습니다.

```bash
# 308과 https://api.twig-tree.com/... Location 확인
curl -I http://api.twig-tree.com/v3/api-docs

# 인증서 검증을 포함한 API 응답: 200 기대
curl -sS -o /dev/null -w '%{http_code}\n' https://api.twig-tree.com/v3/api-docs

# 포트 공개 및 메모리 확인: app의 8080에 호스트 포트 매핑이 없어야 함
docker compose -f compose.yaml -f compose.prod.yaml ps
docker stats --no-stream
free -h

# 재시작 후에도 같은 HTTPS 요청이 성공하는지 확인
docker compose -f compose.yaml -f compose.prod.yaml restart caddy
curl -sS -o /dev/null -w '%{http_code}\n' https://api.twig-tree.com/v3/api-docs
```

외부 PC에서도 HTTPS 응답을 확인하고, `http://<OCI공인IP>:8080`으로 직접 접근할 수 없는지 확인합니다.
정상 동작 확인 후 기존 OCI TCP 8080 인그레스 규칙을 제거합니다.
재시작 검증은 실제 갱신 시점의 성공을 증명하지 않으므로, 운영 중 인증서 만료일과 Caddy 갱신 오류 로그도 확인합니다.
프론트의 CORS 및 쿠키 인증 연동은 후속 작업이며 이 단계의 API 검증은 브라우저 직접 접속 또는 curl로 수행합니다.

## 4. 리소스 배분

인스턴스는 1GB(VM.Standard.E2.1.Micro)이며 Caddy 추가 후 컨테이너 메모리 상한 합계는 864m입니다. 아래 기존 서비스 실측값은 Caddy 추가 전 워밍업(swagger · api-docs · 인증 필요 API 각 5회)을 마친 값입니다. Caddy의 64m는 초기 상한으로, 인증서 발급과 실제 요청 처리 후 다시 측정합니다.

| 서비스 | mem_limit | 실서버 실측 | 로컬 실측 | 비고 |
|---|---|---|---|---|
| db | 176m | 11MiB | 33MiB | `shared_buffers=48MB`, `max_connections=20` (HikariCP 기본 풀 10) |
| redis | 48m | 2MiB | 5MiB | `maxmemory 24mb`, eviction 없음 (refresh token 유실 방지) |
| app | 576m | 297MiB | 472MiB | 힙 상한 256m 고정 |
| caddy | 64m | 미측정 | 미측정 | 인증서 발급·기동 후 측정 필요 |

로컬(Docker Desktop / WSL2)이 더 크게 나오는 것은 cgroup 통계에 잡히는 범위와 워밍업 정도가 달라서입니다. 한도 산정은 큰 쪽인 로컬 값을 기준으로 잡았으므로 실서버에서는 여유가 더 있습니다.

### OS · dockerd 몫은 약 390MiB

Ubuntu 이미지는 snapd · unattended-upgrades 등을 기본으로 돌려서 생각보다 무겁습니다. 실측:

```
Mem: 954Mi total, 690Mi used, 263Mi available    # 컨테이너 합계는 310MiB
```

컨테이너 한도 총합 864m를 다 쓰고 OS·dockerd 약 390MiB를 더하면 약 1254MiB로 954MiB를 넘깁니다. 한도는 목표 사용량이 아닙니다. 위 실측 여유는 Caddy 추가 전 값이므로 배포 후 `free -h`와 `docker stats --no-stream`으로 다시 확인합니다.

메모리가 더 필요하면 컨테이너 한도를 올리기보다 OS 쪽을 덜어내는 편이 효과적입니다(1GB 머신에서 snapd 정리 등).

### 힙은 비율로 주면 안 됩니다

`-XX:MaxRAMPercentage` 는 JVM 이 컨테이너 메모리 한도를 인식한다는 전제에서만 동작합니다. 이 환경에서는 인식이 실패해서, `mem_limit: 512m` 인 컨테이너 안에서 JVM 이 최대 힙을 **3.83G**(호스트 RAM 기준)로 잡았습니다. 그 결과 cgroup 한도를 반복해서 치며 상시 스래싱 상태가 됩니다.

그래서 `-Xms128m -Xmx256m` 으로 절대값을 못 박았습니다. 인식 성공 여부와 무관하게 동일하게 동작합니다. 이 설정에서는 컨테이너 스왑과 한도 접촉이 모두 0 이고, 워밍업 중에도 스왑이 늘지 않습니다.

메타스페이스에는 **상한을 두지 않았습니다.** 실사용 커밋량이 91.6MB(NMT 기준)라 96m 로 조였을 때 첫 요청 처리 중 `OutOfMemoryError: Metaspace` 로 요청 스레드가 죽습니다. 총량은 `mem_limit` 이 막아주므로 이중으로 조일 필요가 없습니다.

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
# 종료 (DB 및 인증서 볼륨 유지)
docker compose -f compose.yaml -f compose.prod.yaml down
```

`down -v`는 DB와 인증서 볼륨을 삭제하므로 배포·롤백에 사용하지 않습니다.

롤백 시에는 작업 파일을 확인한 뒤, 기록해 둔 이전 배포 커밋으로 전환합니다. 아래 자리표시자를 실제 SHA로 바꾸고 각 명령의 성공을 확인합니다.

```bash
git status --short
git switch --detach <이전_배포_커밋_SHA>
docker compose -f compose.yaml -f compose.prod.yaml config --quiet
docker compose -f compose.yaml -f compose.prod.yaml build app
docker compose -f compose.yaml -f compose.prod.yaml up -d --remove-orphans
docker compose -f compose.yaml -f compose.prod.yaml ps
```

HTTPS 도입 전 커밋으로 돌아가면 Caddy가 제거되고 앱의 8080 공개가 복구됩니다. HTTPS는 중단되며, 외부 HTTP 접근까지 복구해야 한다면 제거했던 OCI 8080 규칙을 의도적으로 다시 설정해야 합니다. 인증서 볼륨은 유지됩니다.

Flyway 마이그레이션은 코드를 되돌려도 **자동으로 롤백되지 않습니다.** 스키마 변경이 포함된 배포를 되돌릴 때는 DB 상태를 직접 확인해야 합니다.

## 자주 겪는 문제

- **app 이 `Migration ... failed` 로 죽음**: 기존 데이터가 새 제약을 위반하는 경우입니다. 예를 들어 V7 은 `nodes` · `workspaces` · `folders` 의 `name` 을 `VARCHAR(30)` 으로 줄이므로, 30자를 넘는 기존 행이 있으면 실패합니다. 실패한 마이그레이션과 대상 데이터를 확인하고 백업 후 수정합니다. 전체 볼륨 삭제로 해결하지 않습니다.
- **빌드 중 서버가 멈춘 것처럼 느려짐**: Gradle 빌드가 스왑을 쓰는 중입니다. `free -h` 로 스왑 사용량을 확인하고 기다리세요. 스왑이 없다면 먼저 잡아야 합니다.
- **외부에서 HTTPS 접속 불가**: DNS의 A/AAAA 레코드, OCI 보안 목록/NSG의 TCP 80·443, 서버 방화벽 및 Caddy 포트 매핑을 확인하세요.
- **인증서 발급 실패**: Caddy 로그에서 원인을 확인하고 DNS 전파, 잘못된 AAAA, 80·443 접근, 외부 DNS/HTTPS 통신 및 인증서 볼륨 쓰기 권한을 확인합니다. 인증서 볼륨을 지우며 재발급을 반복하지 않습니다.
- **502 응답**: Spring이 준비됐는지 app 로그를 확인하고, Caddy와 app이 같은 Compose 네트워크에 있는지 확인합니다.
- **Caddy가 반복 재시작**: 컨테이너 종료 상태와 OOM 여부, 호스트 메모리를 확인하고 64m 상한의 적정성을 재평가합니다.
