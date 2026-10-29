#!/usr/bin/env bash

# EC2에서 실행하는 운영 배포 진입점.
# GitHub Actions가 SSM Run Command로 이 스크립트를 호출한다.
# 비밀값은 Parameter Store에서 읽어 현재 프로세스 환경으로만 전달하며 파일로 저장하지 않는다.

set -Eeuo pipefail
umask 077

log() { printf '\n==> %s\n' "$*"; }
fail() { printf '[deploy] %s\n' "$*" >&2; exit 1; }

for command_name in aws curl docker flock; do
	command -v "$command_name" >/dev/null 2>&1 || fail "필수 명령을 찾을 수 없습니다: ${command_name}"
done

docker compose version >/dev/null 2>&1 || fail "Docker Compose plugin을 사용할 수 없습니다."

: "${AWS_REGION:?AWS_REGION is required}"
: "${APP_IMAGE:?APP_IMAGE is required}"
: "${DEPLOY_SHA:?DEPLOY_SHA is required}"

if [[ ! "$DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]]; then
	fail "DEPLOY_SHA는 40자리 소문자 commit SHA여야 합니다."
fi

if [[ "$APP_IMAGE" != *":${DEPLOY_SHA}" ]]; then
	fail "APP_IMAGE 태그와 DEPLOY_SHA가 일치하지 않습니다."
fi

parameter_path=${PARAMETER_PATH:-/twigtree/prod}
healthcheck_url=${HEALTHCHECK_URL:-https://api.twig-tree.com/v3/api-docs}
healthcheck_attempts=${HEALTHCHECK_ATTEMPTS:-36}

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd -- "$script_dir/../.." && pwd)
state_dir="$repo_dir/.deploy"
mkdir -p "$state_dir"

exec 9>"$state_dir/deploy.lock"
flock -n 9 || fail "다른 배포가 진행 중입니다."

compose=(docker compose -f "$repo_dir/compose.yaml" -f "$repo_dir/compose.prod.yaml")

get_parameter() {
	local name=$1
	local value
	value=$(aws ssm get-parameter \
		--region "$AWS_REGION" \
		--name "${parameter_path}/${name}" \
		--with-decryption \
		--query 'Parameter.Value' \
		--output text)
	[[ -n "$value" && "$value" != "None" ]] || fail "Parameter Store 값이 비어 있습니다: ${parameter_path}/${name}"
	printf '%s' "$value"
}

log "Parameter Store에서 운영 설정 로드"
export DB_PASSWORD
export JWT_SECRET
export GOOGLE_CLIENT_IDS
export OPENAI_API_KEY
DB_PASSWORD=$(get_parameter DB_PASSWORD)
JWT_SECRET=$(get_parameter JWT_SECRET)
GOOGLE_CLIENT_IDS=$(get_parameter GOOGLE_CLIENT_IDS)
OPENAI_API_KEY=$(get_parameter OPENAI_API_KEY)
export OPENAI_MODEL=${OPENAI_MODEL:-gpt-4o-mini}
export DEV_TEST_TOKEN_ENABLED=false
export APP_IMAGE

registry=${APP_IMAGE%%/*}
[[ "$registry" != "$APP_IMAGE" ]] || fail "APP_IMAGE에서 ECR registry를 판별할 수 없습니다."

log "ECR 로그인 및 이미지 pull: ${APP_IMAGE}"
aws ecr get-login-password --region "$AWS_REGION" |
	docker login --username AWS --password-stdin "$registry" >/dev/null

"${compose[@]}" config --quiet
"${compose[@]}" pull db redis app caddy

previous_image=""
current_container=$("${compose[@]}" ps -q app 2>/dev/null || true)
if [[ -n "$current_container" ]]; then
	previous_image=$(docker inspect --format '{{.Config.Image}}' "$current_container" 2>/dev/null || true)
fi

wait_for_health() {
	local label=$1
	local attempt
	for ((attempt = 1; attempt <= healthcheck_attempts; attempt++)); do
		if curl --fail --silent --show-error --max-time 10 "$healthcheck_url" >/dev/null; then
			printf '[deploy] %s HTTPS 검증 성공 (%d/%d)\n' "$label" "$attempt" "$healthcheck_attempts"
			return 0
		fi
		printf '[deploy] %s 기동 대기 (%d/%d)\n' "$label" "$attempt" "$healthcheck_attempts"
		sleep 5
	done
	return 1
}

deployment_started=false

on_error() {
	local status=$?
	trap - ERR
	printf '[deploy] 새 이미지 배포 실패: %s\n' "$APP_IMAGE" >&2
	"${compose[@]}" ps >&2 || true
	"${compose[@]}" logs --tail=100 caddy app >&2 || true

	if [[ "$deployment_started" == true && -n "$previous_image" && "$previous_image" != "$APP_IMAGE" ]]; then
		printf '[deploy] 이전 애플리케이션 이미지로 롤백: %s\n' "$previous_image" >&2
		export APP_IMAGE=$previous_image
		if "${compose[@]}" up -d --no-build --pull never --remove-orphans && wait_for_health "롤백"; then
			printf '[deploy] 애플리케이션 이미지 롤백 성공\n' >&2
		else
			printf '[deploy] 애플리케이션 이미지 롤백 실패. 수동 복구가 필요합니다.\n' >&2
		fi
	else
		printf '[deploy] 사용할 수 있는 이전 이미지가 없어 자동 롤백하지 못했습니다.\n' >&2
	fi

	exit "$status"
}

trap on_error ERR

log "Compose 적용: ${APP_IMAGE}"
deployment_started=true
"${compose[@]}" up -d --no-build --pull never --remove-orphans
wait_for_health "배포"

printf '%s\n' "$APP_IMAGE" >"$state_dir/current-image.tmp"
mv "$state_dir/current-image.tmp" "$state_dir/current-image"

trap - ERR
log "배포 완료: ${APP_IMAGE}"
"${compose[@]}" ps
