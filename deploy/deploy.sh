#!/bin/bash
# 서버(/opt/chat)에서 실행 : ECR 이미지로 스택을 갱신하고 게이트웨이가 뜰 때까지 기다린다.
#   sudo bash deploy/deploy.sh [IMAGE_TAG]
set -euo pipefail

export HOME=${HOME:-/root} # SSM 으로 실행되면 HOME 이 비어 있다 (docker 의 ECR 로그인 설정 위치)
export ECR_REGISTRY=782875439193.dkr.ecr.ap-northeast-2.amazonaws.com
export IMAGE_TAG="${1:-latest}"

cd "$(dirname "$0")/.."
compose() { docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml "$@"; }

echo "== pull ($IMAGE_TAG)"
compose pull --quiet
echo "== up"
compose up -d --no-build --remove-orphans
docker image prune -f > /dev/null

echo "== wait api-gateway"
for i in $(seq 1 60); do
  if curl -fs -o /dev/null http://localhost:8080/; then
    compose ps --format '{{.Service}}\t{{.Status}}'
    echo "deploy ok ($IMAGE_TAG)"
    exit 0
  fi
  sleep 5
done
compose ps
compose logs --tail 50 api-gateway
echo "api-gateway did not respond in 300s" >&2
exit 1
