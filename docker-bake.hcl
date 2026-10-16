# CI 에서 서비스 이미지를 한 번에 빌드한다 (Dockerfile 의 빌드 단계는 공유되어 한 번만 돈다)
#   REGISTRY=... TAG=... docker buildx bake --push

variable "REGISTRY" {
  default = "local"
}

variable "TAG" {
  default = "latest"
}

group "default" {
  targets = ["app"]
}

target "app" {
  name       = svc
  matrix     = { svc = ["service-discovery", "api-gateway", "client-api", "chatting", "kafka-producer", "kafka-consumer", "notification"] }
  context    = "."
  dockerfile = "Dockerfile"
  platforms  = ["linux/amd64"]
  args       = { MODULE = "distributed-chat-system-${svc}" }
  tags = [
    "${REGISTRY}/distributed-chat-system/${svc}:${TAG}",
    "${REGISTRY}/distributed-chat-system/${svc}:latest",
  ]
}
