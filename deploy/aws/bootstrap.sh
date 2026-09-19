#!/usr/bin/env bash
#
# AWS EC2(Ubuntu 24.04 LTS / x86_64) 초기 세팅.
#
# 사용법: 인스턴스에 SSM 으로 접속한 뒤 ubuntu 사용자로 실행한다.
#
#   aws ssm start-session --target <instance-id> --region ap-northeast-2
#   sudo su - ubuntu
#   curl -fsSL <이 파일 raw URL> -o bootstrap.sh && bash bootstrap.sh
#
# 여러 번 실행해도 안전하다(멱등). 실패하면 그 자리에서 멈춘다.
#
# 이 스크립트가 하지 않는 것:
#   - 저장소 clone (배포 파이프라인 구성 시 별도 수행)
#   - .env 생성 (SSM Parameter Store 에서 배포 시점에 주입)
#   - snapd 제거 (아래 "왜 snapd 를 남기는가" 참고)

set -euo pipefail

SWAP_SIZE=2G
SWAP_FILE=/swapfile
TIMEZONE=Asia/Seoul

log() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
warn() { printf '\n\033[1;33m[!] %s\033[0m\n' "$*"; }

# ── 0. 사전 확인 ─────────────────────────────────────────────
log "환경 확인"

if [[ $EUID -eq 0 ]]; then
	echo "root 로 실행하지 마세요. ubuntu 사용자로 실행하면 내부에서 sudo 를 씁니다." >&2
	echo "docker 그룹에 root 를 넣어봐야 의미가 없습니다." >&2
	exit 1
fi

source /etc/os-release
if [[ "${VERSION_ID:-}" != "24.04" ]]; then
	warn "Ubuntu 24.04 를 기준으로 작성된 스크립트입니다. 현재: ${PRETTY_NAME:-unknown}"
	read -rp "그래도 계속할까요? [y/N] " answer
	[[ "$answer" == "y" ]] || exit 1
fi

echo "OS       : ${PRETTY_NAME}"
echo "아키텍처 : $(dpkg --print-architecture)"
echo "메모리   : $(free -h | awk '/^Mem:/ {print $2}')"
echo "디스크   : $(df -h / | awk 'NR==2 {print $2}')"

# ── 1. 시간대 ────────────────────────────────────────────────
log "시간대를 ${TIMEZONE} 로 설정"
# 로그 타임스탬프가 UTC 로 찍히면 장애 대응 중에 계속 암산해야 한다.
sudo timedatectl set-timezone "$TIMEZONE"
timedatectl | grep 'Time zone'

# ── 2. 스왑 ──────────────────────────────────────────────────
log "스왑 ${SWAP_SIZE} 구성"
# 2GB 인스턴스라 상시 스왑을 쓸 일은 없어야 정상이다.
# 문서 파싱(PDFBox·POI)이 순간적으로 힙을 밀어올릴 때 OOM Kill 대신
# 느려지는 쪽으로 버티게 하는 보험이다.
if swapon --show=NAME --noheadings | grep -qx "$SWAP_FILE"; then
	echo "이미 활성화됨: $(swapon --show=NAME,SIZE --noheadings | tr '\n' ' ')"
else
	sudo fallocate -l "$SWAP_SIZE" "$SWAP_FILE"
	sudo chmod 600 "$SWAP_FILE"
	sudo mkswap "$SWAP_FILE"
	sudo swapon "$SWAP_FILE"
	echo "생성 완료"
fi

# 재부팅 후에도 유지
if ! grep -qF "$SWAP_FILE" /etc/fstab; then
	echo "$SWAP_FILE none swap sw 0 0" | sudo tee -a /etc/fstab >/dev/null
	echo "/etc/fstab 등록"
fi

# swappiness 기본값 60 은 메모리가 남아도 스왑을 쓴다.
# JVM 힙이 스왑으로 밀리면 GC 때마다 디스크를 긁어서 응답이 무너진다.
# 10 으로 낮춰 정말 부족할 때만 쓰게 한다.
sudo tee /etc/sysctl.d/99-twigtree.conf >/dev/null <<'EOF'
vm.swappiness=10
vm.vfs_cache_pressure=50
EOF
sudo sysctl --quiet --load /etc/sysctl.d/99-twigtree.conf
echo "swappiness = $(cat /proc/sys/vm/swappiness)"

# ── 3. 패키지 업데이트 ───────────────────────────────────────
log "패키지 목록 갱신 및 기본 도구 설치"
sudo apt-get update -qq
sudo apt-get install -y -qq \
	ca-certificates curl gnupg unzip git jq postgresql-client

# ── 4. Docker ────────────────────────────────────────────────
log "Docker CE 설치"
if command -v docker >/dev/null 2>&1; then
	echo "이미 설치됨: $(docker --version)"
else
	sudo install -m 0755 -d /etc/apt/keyrings
	curl -fsSL https://download.docker.com/linux/ubuntu/gpg |
		sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
	sudo chmod a+r /etc/apt/keyrings/docker.gpg

	echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" |
		sudo tee /etc/apt/sources.list.d/docker.list >/dev/null

	sudo apt-get update -qq
	sudo apt-get install -y -qq \
		docker-ce docker-ce-cli containerd.io \
		docker-buildx-plugin docker-compose-plugin
fi

# ── 5. Docker 데몬 설정 ──────────────────────────────────────
log "Docker 데몬 설정"
# 로그 로테이션은 compose.prod.yaml 에서 서비스별로도 지정하지만,
# 데몬 기본값으로도 걸어둔다. 일회성 컨테이너(pg_dump 등)가 부트 볼륨을
# 채우는 것을 막는다.
#
# live-restore: dockerd 를 재시작해도 컨테이너를 죽이지 않는다.
# Docker 업그레이드 중 서비스가 끊기는 것을 막아준다.
sudo tee /etc/docker/daemon.json >/dev/null <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "live-restore": true
}
EOF
sudo systemctl restart docker
sudo systemctl enable --now docker

# ── 6. docker 그룹 ───────────────────────────────────────────
log "ubuntu 사용자를 docker 그룹에 추가"
if id -nG "$USER" | tr ' ' '\n' | grep -qx docker; then
	echo "이미 포함됨"
else
	sudo usermod -aG docker "$USER"
	warn "그룹 변경은 재로그인해야 적용됩니다. 세션을 끊었다가 다시 붙으세요."
fi

# ── 7. AWS CLI v2 ────────────────────────────────────────────
log "AWS CLI v2 설치"
# apt 의 awscli 는 v1 이라 ECR 로그인·Parameter Store 사용에 불편하다.
# 배포 스크립트가 `aws ecr get-login-password` 와
# `aws ssm get-parameters` 를 쓰므로 v2 를 직접 설치한다.
if command -v aws >/dev/null 2>&1 && aws --version 2>&1 | grep -q 'aws-cli/2'; then
	echo "이미 설치됨: $(aws --version 2>&1)"
else
	tmp=$(mktemp -d)
	curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "$tmp/awscliv2.zip"
	unzip -q "$tmp/awscliv2.zip" -d "$tmp"
	sudo "$tmp/aws/install" --update
	rm -rf "$tmp"
	echo "$(aws --version 2>&1)"
fi

# ── 8. 검증 ──────────────────────────────────────────────────
log "검증"

echo "--- 버전 ---"
docker --version
docker compose version
aws --version 2>&1
git --version
psql --version

echo
echo "--- SSM 에이전트 (유일한 접속 경로) ---"
# 22번 포트를 열지 않으므로 이 에이전트가 죽으면 인스턴스에 들어갈 수 없다.
if snap list amazon-ssm-agent >/dev/null 2>&1; then
	snap services amazon-ssm-agent
elif systemctl is-active --quiet amazon-ssm-agent; then
	systemctl --no-pager status amazon-ssm-agent | head -3
else
	warn "SSM 에이전트를 찾지 못했습니다. 세션이 끊기면 재접속이 불가능할 수 있습니다."
fi

echo
echo "--- IAM 인스턴스 프로파일 ---"
# IMDSv2 토큰 방식. Ubuntu 24.04 AMI 는 IMDSv2 를 요구한다.
token=$(curl -fsS -X PUT "http://169.254.169.254/latest/api/token" \
	-H "X-aws-ec2-metadata-token-ttl-seconds: 60" 2>/dev/null || true)
if [[ -n "$token" ]]; then
	role=$(curl -fsS -H "X-aws-ec2-metadata-token: $token" \
		"http://169.254.169.254/latest/meta-data/iam/security-credentials/" 2>/dev/null || true)
	if [[ -n "$role" ]]; then
		echo "연결된 역할: $role"
	else
		warn "인스턴스 프로파일이 붙어 있지 않습니다. ECR pull 과 Parameter Store 접근이 실패합니다."
		echo "    콘솔에서 인스턴스 > 작업 > 보안 > IAM 역할 수정 으로 지금 붙일 수 있습니다."
	fi
fi

echo
echo "--- 메모리 / 스왑 ---"
free -h

echo
echo "--- 디스크 ---"
df -h /

log "완료"
cat <<'EOF'

다음 순서:
  1. 재로그인 (docker 그룹 적용). 확인: docker ps 가 sudo 없이 되면 성공.
  2. 인스턴스 프로파일이 안 붙어 있다면 콘솔에서 연결.
  3. Elastic IP 할당 및 연결.
  4. 저장소 clone → ECR 로그인 → 배포.

EOF
