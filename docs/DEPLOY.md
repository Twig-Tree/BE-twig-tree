# AWS 운영 배포 가이드

로컬 개발 환경은 [SETUP.md](SETUP.md)를 참고하세요. 이 문서는 GitHub Actions, ECR,
SSM Run Command를 이용해 AWS EC2 운영 환경을 배포하는 절차를 설명합니다.

## 1. 배포 구조

```text
feature branch
└─ PR → develop
   ├─ CI / Test
   └─ CodeQL / Analyze (java-kotlin)

develop
└─ 승격 PR → main
   ├─ CI / Test
   ├─ CodeQL / Analyze (java-kotlin)
   └─ 사람 승인

main merge
└─ GitHub production Environment
   └─ OIDC → AWS deploy role
      ├─ Docker image → ECR (commit SHA tag)
      └─ SSM Run Command → EC2
         └─ deploy/aws/deploy.sh
            ├─ Parameter Store 설정 로드
            ├─ ECR image pull
            ├─ Docker Compose 적용
            ├─ HTTPS 검증
            └─ 실패 시 이전 애플리케이션 이미지 복구
```

기본 브랜치는 `develop`입니다. `main`은 운영에 배포된 코드만 보관합니다. 배포 workflow는
`main` push와 `main`에서 시작한 수동 실행만 허용합니다.

## 2. 운영 Compose

운영에서는 다음 두 파일을 함께 사용합니다.

```bash
docker compose -f compose.yaml -f compose.prod.yaml ...
```

| 파일 | 역할 |
|---|---|
| `compose.yaml` | db, redis, app 공통 정의와 로컬 app 빌드 |
| `compose.prod.yaml` | ECR app 이미지, Caddy, 운영 메모리·로깅·프로파일 |

`compose.prod.yaml`의 app은 `APP_IMAGE`가 반드시 필요합니다. 배포 스크립트가 full commit SHA로
태그된 ECR 이미지 URI를 환경변수로 전달하고 `--no-build`로 적용합니다. 운영 EC2에서 소스를
Gradle로 다시 빌드하지 않습니다.

## 3. AWS 사전 조건

### EC2

- Ubuntu 24.04
- Docker CE와 Docker Compose plugin
- AWS CLI v2
- SSM Agent Online
- `ubuntu` 사용자가 Docker를 실행할 수 있음
- 저장소를 `/opt/twigtree`에 clone할 수 있는 외부 HTTPS 통신
- 80·443 인바운드 허용, 22 인바운드 불필요

초기 서버 구성에는 [bootstrap.sh](../deploy/aws/bootstrap.sh)를 사용합니다.

### ECR

- 저장소: GitHub `production` Environment의 `ECR_REPOSITORY` 변수로 지정
- image scan on push 활성화
- tag immutability 활성화 권장
- lifecycle policy로 오래된 이미지 정리

이미지 태그는 `latest`가 아니라 40자리 Git commit SHA입니다. workflow를 재실행했을 때 같은
SHA 이미지가 이미 있으면 빌드를 건너뛰고 기존 이미지를 재사용합니다.

### Parameter Store

`PARAMETER_PATH` 아래에 다음 `SecureString`이 필요합니다.

```text
DB_PASSWORD
JWT_SECRET
GOOGLE_CLIENT_IDS
OPENAI_API_KEY
```

기본 경로는 `/twigtree/prod`입니다. 배포 스크립트는 값을 파일에 저장하지 않고 현재 프로세스의
환경변수로만 Compose에 전달합니다. `OPENAI_MODEL`은 지정하지 않으면 `gpt-4o-mini`,
`DEV_TEST_TOKEN_ENABLED`는 항상 `false`입니다.

### EC2 인스턴스 역할

EC2 역할에는 다음 권한만 필요합니다.

- `AmazonSSMManagedInstanceCore`
- ECR 인증 토큰과 지정 저장소 pull
- 지정 Parameter Store 경로 읽기
- SSM Parameter Store를 경유한 KMS 복호화

## 4. GitHub OIDC와 배포 역할

장기 AWS Access Key를 GitHub Secret에 저장하지 않습니다. AWS에 GitHub OIDC Provider와 배포
역할을 만들고, trust policy의 subject를 production Environment로 제한합니다.

```text
token.actions.githubusercontent.com:aud = sts.amazonaws.com
token.actions.githubusercontent.com:sub = repo:Twig-Tree/BE-twig-tree:environment:production
```

배포 역할에는 다음 최소 권한이 필요합니다.

- 지정 ECR 저장소 image push·조회
- `ecr:GetAuthorizationToken`
- 지정 EC2에 `ssm:SendCommand`
- `AWS-RunShellScript` 문서 사용
- 실행한 명령의 `ssm:GetCommandInvocation`

## 5. GitHub production Environment

`production` Environment를 만들고 deployment branch policy를 `main`으로 제한합니다. 다음 값은
Secret이 아니라 Environment variable입니다.

| 변수 | 예시/의미 |
|---|---|
| `AWS_REGION` | `ap-northeast-2` |
| `AWS_ROLE_ARN` | GitHub OIDC 배포 역할 ARN |
| `ECR_REPOSITORY` | ECR 저장소 이름 |
| `EC2_INSTANCE_ID` | 배포 대상 인스턴스 ID |
| `PARAMETER_PATH` | `/twigtree/prod` |
| `HEALTHCHECK_URL` | `https://api.twig-tree.com/v3/api-docs` |
| `PRODUCTION_URL` | `https://api.twig-tree.com` |

애플리케이션 비밀값은 GitHub에 복사하지 않습니다.

## 6. main 보호 규칙

`main`에는 `develop`과 별도의 ruleset을 적용합니다.

- Pull Request 필수
- 사람 승인 최소 1명
- 리뷰 대화 해결 필수
- force push·삭제 금지
- squash merge만 허용
- `Test` required check
- `Analyze (java-kotlin)` required check

CI와 CodeQL workflow는 `develop`, `main` 대상 PR과 push에서 모두 실행됩니다.

## 7. 자동 배포 순서

`.github/workflows/deploy.yml`은 다음 순서로 실행됩니다.

1. 실행 ref가 `main`인지 확인
2. production Environment 변수 형식 검증
3. OIDC로 AWS 배포 역할 획득
4. ECR에서 commit SHA 이미지 존재 여부 확인
5. 이미지가 없으면 `linux/amd64`로 build·push
6. SSM Run Command로 EC2의 저장소를 workflow commit에 맞춤
7. `deploy/aws/deploy.sh` 실행
8. HTTPS health check 성공 확인
9. GitHub deployment 성공 또는 실패 기록

production concurrency는 실행 중 배포를 취소하지 않습니다. 동시에 여러 배포가 서버 상태를
덮어쓰지 않도록 서버 스크립트도 `flock`으로 중복 실행을 거부합니다.

## 8. EC2 배포 스크립트

[deploy.sh](../deploy/aws/deploy.sh)는 다음 안전 검사를 수행합니다.

- 40자리 commit SHA와 ECR image tag 일치 확인
- Parameter Store 필수값 누락 확인
- 비밀값을 로그나 `.env`에 기록하지 않음
- Compose 설정 검증 후 image pull
- 기존 app 컨테이너의 image reference 기록
- `--no-build`로 새 이미지 적용
- HTTPS endpoint를 최대 3분 동안 재시도
- 실패 시 기존 컨테이너가 사용하던 이미지로 app 복구 시도

첫 ECR 배포 전에도 기존 컨테이너가 있으면 해당 컨테이너의 image reference를 롤백 대상으로
사용합니다. 기존 이미지가 없거나 이미 삭제됐다면 자동 롤백할 수 없으므로 수동 복구가
필요합니다.

## 9. 수동 재배포와 롤백

GitHub Actions의 `Deploy production` workflow에서 `Run workflow`를 선택하고, `main` ref와 기존
ECR image의 full commit SHA를 `image_tag`에 입력합니다.

```text
image_tag = 40자리 기존 commit SHA
```

workflow는 해당 태그가 ECR에 있는지 먼저 확인하고, 새로 빌드하지 않은 채 같은 SSM 배포 절차를
실행합니다. 빈 값으로 실행하면 현재 `main` SHA 이미지를 빌드하거나 재사용합니다.

## 10. 운영 확인

배포 후 다음을 확인합니다.

```bash
# HTTP → HTTPS 전환
curl -I http://api.twig-tree.com/v3/api-docs

# 인증서 검증을 포함한 운영 응답
curl -sS -o /dev/null -w '%{http_code}\n' https://api.twig-tree.com/v3/api-docs

# SSM Session Manager로 서버에 접속한 경우
cd /opt/twigtree
docker compose -f compose.yaml -f compose.prod.yaml ps
docker compose -f compose.yaml -f compose.prod.yaml logs --tail=100 caddy app
docker stats --no-stream
free -h
```

HTTPS 검증에는 인증서 검증을 생략하는 `curl -k`를 사용하지 않습니다.

## 11. DB와 롤백 한계

이번 CD 구성은 DB 백업을 만들지 않습니다. 자동 롤백은 **애플리케이션 이미지에만** 적용됩니다.

- Flyway migration은 애플리케이션 이미지를 되돌려도 자동으로 취소되지 않습니다.
- migration은 이전 애플리케이션과 호환되는 확장 방식으로 작성해야 합니다.
- 컬럼 삭제·이름 변경·타입 축소처럼 되돌리기 어려운 migration은 별도 운영 절차가 필요합니다.
- EC2 또는 루트 EBS가 삭제되면 현재 Docker volume의 DB를 복구할 외부 백업이 없습니다.

DB 백업 정책이 도입되기 전까지 이 위험을 수용한 상태로 운영합니다.

## 12. 알려진 운영 특성

- 단일 EC2·단일 app 컨테이너이므로 재생성 중 잠시 502가 발생할 수 있습니다.
- Caddy 인증서 데이터와 PostgreSQL 데이터는 Docker volume에 유지됩니다.
- `down -v`는 DB와 인증서 볼륨을 삭제하므로 배포·롤백에 사용하지 않습니다.
- Redis refresh token 데이터는 영구 백업 대상이 아니며 손실되면 사용자가 다시 로그인해야 합니다.
