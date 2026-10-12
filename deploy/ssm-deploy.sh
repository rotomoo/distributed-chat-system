#!/bin/bash
# GitHub Actions 러너에서 실행 : SSM 으로 chat-server 에 배포 명령을 보내고 끝날 때까지 기다린다.
#   deploy/ssm-deploy.sh <git ref> <image tag>
set -euo pipefail
GIT_REF="$1"
IMAGE_TAG="$2"

IID=$(aws ec2 describe-instances --filters Name=tag:Name,Values=chat-server Name=instance-state-name,Values=running \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)

# 방금 켠 서버면 SSM 에이전트가 붙을 때까지 기다린다
for i in $(seq 1 60); do
  online=$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$IID" \
    --query 'InstanceInformationList[0].PingStatus' --output text)
  [ "$online" = "Online" ] && break
  sleep 5
done

COMMAND="set -e; cd /opt/chat && git fetch --quiet origin && git checkout --quiet --force ${GIT_REF} && bash deploy/deploy.sh ${IMAGE_TAG}"
CMD_ID=$(aws ssm send-command --instance-ids "$IID" --document-name AWS-RunShellScript \
  --comment "deploy ${IMAGE_TAG:0:12}" --timeout-seconds 1200 \
  --parameters "$(jq -cn --arg c "$COMMAND" '{commands: [$c], executionTimeout: ["1200"]}')" \
  --query Command.CommandId --output text)
echo "SSM command: $CMD_ID"

while true; do
  status=$(aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$IID" --query Status --output text 2>/dev/null || echo Pending)
  case "$status" in
    Pending|InProgress|Delayed) sleep 10 ;;
    *) break ;;
  esac
done

aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$IID" --query StandardOutputContent --output text
aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$IID" --query StandardErrorContent --output text >&2
echo "status: $status"
[ "$status" = "Success" ]
