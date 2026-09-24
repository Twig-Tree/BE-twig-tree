#!/usr/bin/env bash
#
# AWS EC2(Ubuntu 24.04 LTS / amd64 · arm64) 초기 세팅.
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
if [[ "${ID:-}" != "ubuntu" || "${VERSION_ID:-}" != "24.04" ]]; then
	echo "지원하지 않는 OS입니다: ${PRETTY_NAME:-unknown}" >&2
	echo "Ubuntu 24.04에서만 실행할 수 있습니다." >&2
	exit 1
fi

# AWS CLI 는 아키텍처마다 아티팩트가 다르다. 시스템을 건드리기 전에 먼저
# 해석해서, 지원하지 않는 아키텍처면 아무것도 바꾸지 않은 채로 멈춘다.
# (x86_64 URL 을 그대로 쓰면 ARM 에서 설치는 성공하고 실행에서 깨진다.)
DPKG_ARCH=$(dpkg --print-architecture)
case "$DPKG_ARCH" in
	amd64) AWSCLI_ARCH=x86_64 ;;
	arm64) AWSCLI_ARCH=aarch64 ;;
	*)
		echo "지원하지 않는 아키텍처입니다: ${DPKG_ARCH}" >&2
		echo "해당하는 AWS CLI 아티팩트가 없어 진행할 수 없습니다." >&2
		exit 1
		;;
esac

echo "OS       : ${PRETTY_NAME}"
echo "아키텍처 : ${DPKG_ARCH} (AWS CLI: ${AWSCLI_ARCH})"
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
# 설치 여부를 `command -v docker` 로 판단하지 않는다. 그 조건은 우분투 기본
# 패키지(docker.io)나 CLI 만 깔려 있어도 참이 되어, docker-ce 와
# compose plugin 설치를 통째로 건너뛴다. 그러면 아래 검증의
# `docker compose version` 에서 스크립트가 죽는다.
#
# apt 자체가 멱등하므로 조건 없이 매번 그대로 실행한다. 이미 최신이면
# apt-get install 은 아무것도 하지 않는다.
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg |
	sudo gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=${DPKG_ARCH} signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" |
	sudo tee /etc/apt/sources.list.d/docker.list >/dev/null

sudo apt-get update -qq
sudo apt-get install -y -qq \
	docker-ce docker-ce-cli containerd.io \
	docker-buildx-plugin docker-compose-plugin
echo "$(docker --version)"

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
	trap 'rm -rf "$tmp"' EXIT
	awscli_url="https://awscli.amazonaws.com/awscli-exe-linux-${AWSCLI_ARCH}.zip"
	curl -fsSL "$awscli_url" -o "$tmp/awscliv2.zip"
	curl -fsSL "${awscli_url}.sig" -o "$tmp/awscliv2.zip.sig"

	# AWS 공식 문서에 게시된 AWS CLI Team 공개 키를 별도 신뢰 기준으로 둔다.
	# 다운로드한 서명이 이 키로 검증된 경우에만 installer 를 실행한다.
	cat >"$tmp/aws-cli-public-key.asc" <<'EOF'
-----BEGIN PGP PUBLIC KEY BLOCK-----

mQINBF2Cr7UBEADJZHcgusOJl7ENSyumXh85z0TRV0xJorM2B/JL0kHOyigQluUG
ZMLhENaG0bYatdrKP+3H91lvK050pXwnO/R7fB/FSTouki4ciIx5OuLlnJZIxSzx
PqGl0mkxImLNbGWoi6Lto0LYxqHN2iQtzlwTVmq9733zd3XfcXrZ3+LblHAgEt5G
TfNxEKJ8soPLyWmwDH6HWCnjZ/aIQRBTIQ05uVeEoYxSh6wOai7ss/KveoSNBbYz
gbdzoqI2Y8cgH2nbfgp3DSasaLZEdCSsIsK1u05CinE7k2qZ7KgKAUIcT/cR/grk
C6VwsnDU0OUCideXcQ8WeHutqvgZH1JgKDbznoIzeQHJD238GEu+eKhRHcz8/jeG
94zkcgJOz3KbZGYMiTh277Fvj9zzvZsbMBCedV1BTg3TqgvdX4bdkhf5cH+7NtWO
lrFj6UwAsGukBTAOxC0l/dnSmZhJ7Z1KmEWilro/gOrjtOxqRQutlIqG22TaqoPG
fYVN+en3Zwbt97kcgZDwqbuykNt64oZWc4XKCa3mprEGC3IbJTBFqglXmZ7l9ywG
EEUJYOlb2XrSuPWml39beWdKM8kzr1OjnlOm6+lpTRCBfo0wa9F8YZRhHPAkwKkX
XDeOGpWRj4ohOx0d2GWkyV5xyN14p2tQOCdOODmz80yUTgRpPVQUtOEhXQARAQAB
tCFBV1MgQ0xJIFRlYW0gPGF3cy1jbGlAYW1hem9uLmNvbT6JAlQEEwEIAD4CGwMF
CwkIBwIGFQoJCAsCBBYCAwECHgECF4AWIQT7Xbd/1cEYuAURraimMQrMRnJHXAUC
akV0ygUJDqP4lQAKCRCmMQrMRnJHXFHjD/9eyZLYcKuQOlLvtqSDtUBiEZf6ZZjM
i3ygYH8rJNtuToUH+HvSpe819urJCquXhDrlK6N+aqW0hCLtNABJG/vsafIgvIYJ
hSGgpgtNnQyMV1jViRWqPjbouw8OkYKBThUfT1i2Y+wn58ifs6ODBCmTexWtXspA
Si+Gt49xDOW0APmbOPnI+a4HJW6tVEo6MWS0WjzpiBayR3d1A4pt4YrPfSdDgpLo
h2SLQqlRqvvVZJaWBjhkErNFpfsBA06sDcPEOb0G8LBUbR4WOcdvhe5LubJbZuxC
AG9kNPCVeQP1ixwjgjXKysaxeQ6rv0VzIQgRp6tLVLWhy6AKDNvLjFSsmXZ1Wl08
Y/RlOHXlzLuQMRE6sR1wOdRxc9TsrNWTGiBK65cvSWOy03JeBkQQ8pesqltiyxI9
U21kkgiXtTSKNGfKK8pO27D81YANhRqPK7iTp6kuFiY2WtOg90KTMNlIT+Ff85Y2
b1rHj6Z0SrCkJujhWk3IBPic/wJgz01LEc/OAdUPlby90RJZcIBhSlWhT7mXnXIO
c0HWlNQrns2s3CTyYwZSiSlYe9ApeLwhjDo8NhbFuCAy61l6O5UsR4AfZxx/rGKv
2wFb1/RN/P4gNe6vmxZAPjR0AQcwD3tc2McimOLr/22kmPz8IH3I0X7WoSFr0Biz
E91G7bb0hOb/cA==
=knv7
-----END PGP PUBLIC KEY BLOCK-----
EOF

	awscli_key_fingerprint=FB5DB77FD5C118B80511ADA8A6310ACC4672475C
	install -m 0700 -d "$tmp/gnupg"
	actual_fingerprint=$(gpg --batch --homedir "$tmp/gnupg" --with-colons \
		--show-keys "$tmp/aws-cli-public-key.asc" | awk -F: '$1 == "fpr" && !found {print $10; found=1}')
	if [[ "$actual_fingerprint" != "$awscli_key_fingerprint" ]]; then
		echo "AWS CLI 공개 키 지문이 일치하지 않습니다." >&2
		exit 1
	fi
	gpg --batch --homedir "$tmp/gnupg" --import "$tmp/aws-cli-public-key.asc"
	gpg --batch --homedir "$tmp/gnupg" \
		--verify "$tmp/awscliv2.zip.sig" "$tmp/awscliv2.zip"

	unzip -q "$tmp/awscliv2.zip" -d "$tmp"
	sudo "$tmp/aws/install" --update
	rm -rf "$tmp"
	trap - EXIT
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
# 메타데이터 엔드포인트는 EC2 밖에서 응답이 없으므로 타임아웃을 짧게 준다.
token=$(curl -fsS --connect-timeout 1 --max-time 3 \
	-X PUT "http://169.254.169.254/latest/api/token" \
	-H "X-aws-ec2-metadata-token-ttl-seconds: 60" 2>/dev/null || true)
if [[ -n "$token" ]]; then
	role=$(curl -fsS --connect-timeout 1 --max-time 3 \
		-H "X-aws-ec2-metadata-token: $token" \
		"http://169.254.169.254/latest/meta-data/iam/security-credentials/" 2>/dev/null || true)
	if [[ -n "$role" ]]; then
		echo "연결된 역할: $role"
	else
		warn "인스턴스 프로파일이 붙어 있지 않습니다. ECR pull 과 Parameter Store 접근이 실패합니다."
		echo "    콘솔에서 인스턴스 > 작업 > 보안 > IAM 역할 수정 으로 지금 붙일 수 있습니다."
	fi
else
	# 여기서 조용히 넘어가면, 프로파일을 확인하라고 만든 섹션이 아무 출력 없이
	# 지나가 "확인됨"으로 오해된다. 검증 실패를 명시한다.
	warn "IMDSv2 토큰을 받지 못해 IAM 인스턴스 프로파일을 확인하지 못했습니다 (검증 미완료)."
	echo "    EC2 인스턴스가 아니거나 메타데이터 접근이 막힌 환경일 수 있습니다."
	echo "    EC2 라면 배포 전에 반드시 역할 연결 여부를 직접 확인하세요."
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
