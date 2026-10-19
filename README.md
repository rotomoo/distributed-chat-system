# Distributed Chat System

<br>

## 사용법

```
1. cp .env.example .env

2. docker compose up -d --build

3. visit localhost:8080
```

<br>

## 모듈 계층

```
distributed-chat-system
  ├── common    # 1 depth - 공통 기능 모듈
  ├── infra     # 2 depth - 인프라 관련 모듈
  └             # 3 depth - 프로젝트 모듈
```

- distributed-chat-system
    - 📂 common
        - 📁 distributed-chat-system-common
    - 📂 infra
        - 📁 distributed-chat-system-api-gateway
        - 📁 distributed-chat-system-mongodb
        - 📁 distributed-chat-system-mysql
        - 📁 distributed-chat-system-redis
        - 📁 distributed-chat-system-service-discovery
    - 📁 distributed-chat-system-chatting
    - 📁 distributed-chat-system-client-api
    - 📁 distributed-chat-system-message-consumer
    - 📁 distributed-chat-system-kafka-producer
    - 📁 distributed-chat-system-notification-consumer

<br>

## 아키텍처 설계

![architecture](./image/architecture.png)

<br>

# 설계과정

## 기능 요구사항

> DAU(Daily Active User) 1,000,000명 지원 시스템

- 팀 기능
- 실시간 DM(Direct Message), 채널 채팅 기능
- 실시간 팀 사용자 접속상태 표시 기능
- 채팅 메시지 첨부파일(이미지) 지원 기능
- 채팅 메시지 이모티콘 반응 기능
- 채팅 메시지 댓글 기능
- 멘션 기능
- 채팅 메시지 종단 간 암호화 필요
- 모든 채팅 이력 10년 보관
- 다양한 단말 동시 접속 지원
- 푸시 알림 기능

## 개략적 규모 추정

**데이터 저장 전략**

- Mysql

  회원, 팀, 채널 데이터 저장


- MongoDB

  메시지, 이모티콘 반응, 댓글, 메타데이터 저장  
  첨부파일은 GridFS 에 바이너리로 저장  
  <br>
  예시

    ```json
    {
      "messageId": "1353215",
      "channelId": "12345",
      "createUserId": "1142",
      "content": "안녕하세요!",
      "create_dt": "2024-11-18T10:30:00Z",
      "reactions": [
        {
          "emoji": "👍",
          "count": 3
        },
        {
          "emoji": "😂",
          "count": 1
        }
      ],
      "comments": [
        {
          "createUserId": "114346",
          "content": "반가워요!",
          "create_dt": "2024-11-18T10:35:00Z"
        }
      ],
      "attachments": [
        {
          "fileId": "6710a3b1e4b0a1b2c3d4e5f0",
          "name": "photo.jpg",
          "contentType": "image/jpeg",
          "size": 204800
        }
      ]
    }
    ```
  <br>
- Redis

  Shared Session Store - Redis 기반 세션 중앙화  <br>
  [Redis Sentinel (Failover : SPOF 방지, 가용성 확보)](https://rotomoo.tistory.com/101)

<br>

**저장소 요구량**

- DAU (Daily Active Users): 1,000,000명
- 1인당 일일 평균 메시지 전송 수: 20건 → 하루 20,000,000건 (평균 초당 약 230건)
- 최대 텍스트 메시지 크기: 12KB (한글, 4,000자 제한)
- 첨부파일 비율: 20%
- 평균 첨부파일 크기: 200KB
- 연간 메시지 수 = 1,000,000 * 20건 * 365일 = 73억 건 / year
- 연간 메시지 크기 = 1,000,000 * 20건 * 12KB * 365일 = 87.6TB / year
- 연간 첨부파일 수 = 1,000,000 * 20건 * 20% * 365일 = 14.6억 개 / year
- 연간 첨부파일 크기 = 1,000,000 * 20건 * 20% * 200KB * 365일 = 292TB / year
- 10년간 MongoDB 저장소 요구량 = 약 3.8PB
  - 메시지 : 730억 건, 876TB
  - 첨부파일 : 146억 개, 2.92PB

**결론**

- 한 노드에 담을 수 없는 양이므로 분산(샤딩) 필요
- 추후 첨부파일은 온프레미스 방식으로 전환 필요

<br>

**채팅서버 메모리 요구량**

> 모든 실시간 기능은 WebSocket을 통해 동작 ([ref : WebSocket 프로토콜](https://rotomoo.tistory.com/100))

- 채팅 서버 메모리를 통해 채팅 메시지 전송
  - DB 쓰기 트랜잭션 : 초당 약 230건 (N명에게 보내도 1번)
  - 메시지 전송 : 초당 230 × N건, 채팅 서버 메모리로 처리
- 동시 접속자: 100,000명 (DAU 의 10%)
- 접속당 서버 메모리 : 10KB
- 채팅서버 메모리 요구량 = 100,000명 * 10KB = 1GB

**결론**

- 메모리 요구량 및 트래픽 분산이 필요하여 채팅 서버 분산 (채팅 서버 1대당 34,000 연결, 약 340MB)
- 채팅 서버 한 대가 내려가도 채팅이 끊기지 않도록 가용성 확보

## 기능 목록

**(infra) api-gateway server** — 리버스 프록시 (`:8080`)

- 가용 서버 라우팅 (spring-cloud-gateway)
    - 기존 접속 채팅 서버 우선 라우팅
    - 채팅 서버
      로드밸런서 [(spring-cloud-loadbalancer, round-robin)](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/loadbalancer.html)

<br>

**(infra) service-discovery server** — 서비스 레지스트리

- client-side discovery (spring-cloud-eureka)
    - 가용 서버 정보 동기화

<br>

**chatting server**

- 웹소켓 연결 (로그인 세션 확인), 한 유저의 여러 기기 연결을 메모리에 보관
- 클라이언트가 보낸 메시지를 kafka-producer 로 전송
- message-consumer 가 보낸 메시지를 접속한 기기로 전송
- 하트비트 : 25초마다 브라우저에 ping, 응답 없는 연결은 끊음
- 접속 위치와 접속 상태(온라인 / 자리 비움)를 Redis `{userId}` 에 동기화

<br>

**client-api server**

- 회원가입, 로그인 / 로그아웃 (Redis 세션), 내 정보 조회
- 팀 생성 / 가입 / 목록 조회 / 멤버 조회
- 채널 생성 / 참여 / 목록 조회
- DM(1:1 대화) 시작 / 목록 조회
- 이전 메시지 조회, 재접속 시 놓친 메시지(멱등) 조회
- 읽음 위치 저장, 채널별 안 읽은 메시지 수
- 채널 알림 끄기 / 켜기, 웹 푸시 구독 / 해제
- 첨부파일 업로드 / 다운로드
- 팀 사용자 접속상태 목록 조회 (Redis `{userId}` 의 status → 온라인 / 자리 비움 / 오프라인)

<br>

**message-consumer server**

- 채팅 메시지 이벤트 소비
- 보낸 사람이 채널 멤버인지 확인 (MySQL)
- 메시지 MongoDB 에 저장 (eventId 로 멱등 저장)
- 채널 멤버가 접속한 채팅 서버를 Redis 에서 찾아(MGET) 서버별로 전달 (HTTP, 500명씩)
- 오프라인 멤버는 kafka-producer 에 알림 발행 요청 (HTTP)
- 실패하면 재시도 토픽으로 다시 처리 (1초 → 5초 → 30초), 끝까지 실패하면 DLT 에 남기고 로그

<br>

**kafka-producer server**

- 채팅 메시지 / 리액션 이벤트 발행 (채널 ID 를 키로 → 같은 채널은 같은 파티션, 채널 안 순서 보장)
- 오프라인 멤버 알림 이벤트 발행 (chat-notification 토픽, message-consumer 가 요청)

<br>

**notification-consumer server**

- 오프라인 멤버에게 웹 푸시 (chat-notification 토픽 소비)
    - 알림 끈 채널은 보내지 않는다, 같은 유저 / 채널은 30초에 한 번

<br>

## DB 설계

**Mysql** — 회원, 팀, 채널, 채널 멤버 (원본)

- 도메인 모델 분석
  ![rdb_domain](./image/rdb_domain.png)
- 테이블 설계
  ![rdb_table](./image/rdb_table.png)
- 분산 처리
    - Replication : 주(master) - 쓰기 연산, 부(slave) - 읽기 연산
    - Failover : 가용성 확보

<br>

**MongoDB** — 메시지, 이모지 리액션, 첨부파일

- 첨부파일 본문은 GridFS 에 바이너리(chunk)로 저장하고, 메시지에는 fileId 와 이름 / 타입 / 크기만 둔다
- 컬렉션 설계
- 분산 처리
    - 샤딩 키 : ChannelId

**Redis** — 접속 정보, 로그인 세션 공유

- [Redis Sentinel (Failover : SPOF 방지, 가용성 확보)](https://rotomoo.tistory.com/101)

| 키 | 값 | TTL | 쓰는 쪽 | 읽는 쪽 |
|---|---|---|---|---|
| `{userId}` | `{"server":"ws://ip:port","status":"online"\|"away"}` | 60초 (유저당 20초마다 갱신) | chatting | api-gateway (라우팅), message-consumer (전달할 서버), client-api (접속 상태) |
| `distributed-chat-system:sessions:{sessionId}` | 로그인 세션 (Hash, Spring Session) | 30일 | client-api | chatting (웹소켓 연결 시 로그인 확인) |
| `notify:throttle:{userId}:{channelId}` | `1` | 30초 | notification-consumer | notification-consumer (같은 채널 웹 푸시 30초에 한 번) |

- TTL을 통해 접속상태 관리 (채팅 서버가 내려가도 60초 후 만료)

<br>

**채팅 서버 메모리**

| 이름 | 값 | 쓰임 |
|---|---|---|
| `webSocketSessionMap` | userId → 그 유저의 기기 연결들 (sessionId → WebSocketSession), 자리 비움을 보낸 기기, Redis 에 마지막으로 쓴 상태 / 시각 | 받은 메시지를 기기마다 보낸다, 기기들을 합쳐 온라인 / 자리 비움을 정한다 |
| `lastSeenAtBySession` | sessionId → 마지막으로 프레임을 받은 시각 | 하트비트 : 응답 없는 연결을 찾아 끊는다 |

<br>

## 캐시 계층

**Redis [Look Aside + Write Around 전략](https://rotomoo.tistory.com/99)**
![caching-strategies](./image/caching-strategies.png)
**CDN 적용** (나중에 적용해보기)

<br>

## 요구사항 검증
- 슬랙, 디스코드 같은 대규모 트래픽 서비스를 경험해 보고자 DAU 100만 명 규모의 트래픽을 감당할 수 있는 채팅 시스템 아키텍처를 수립하고 데이터 아카이빙 전략을 설계
- 팀 단위 협업 시 개발 생산성을 높이고, 기능 변경과 확장이 용이한 구조를 만들기 위해 서비스별 책임이 명확히 분리된 멀티 모듈 구조를 구축하여 유지보수성과 테스트 효율성을 확보
- RDB로는 기하급수적으로 늘어나는 채팅 데이터의 쓰기/조회 부하를 감당하기 어렵다고 판단하여 샤딩이 가능한 MongoDB 기반으로 채널 단위 메시지 컬렉션을 설계하고, WebSocket 기반 실시간 1:1 및 채널 채팅을 구현
    - 카프카를 Stomp로 사용하는것보단 ‘메시지 전송’ 같은 이벤트를 발행해서 컨슈머에서 관리하는게 맞다고 생각함. 채팅 서버간 의존도 안하게 되어 느슨한 결합도됨.
- 컨슈머 서버를 통해 채팅 서버 간 직접 연결 없이 메시지 전달 책임을 분리
    - 컨슈머 서버에서 Redis에 저장되어있는 세션 데이터들을 통해 접속한 채팅서버로 메시지를 전달.
    - 이때 컨슈머 서버에서 해당 데이터를 MongoDB에 저장.
    - 실제 전달할 메시지는 채팅서버 메모리를 통해 전송, 이전 메시지는 채팅서버 접속시 MongoDB로 조회.
    어차피 채널 단위로 샤딩이 되어있고 가장 최근 데이터 몇개만 slice로 조회하면 성능도 괜찮을거라 생각됨.
- Spring Cloud를 도입하여 클라이언트 사이드 서비스 디스커버리 패턴 적용.
    - 서버 스케일 아웃, 채팅서버 라우팅 등의 확장에 유리한 아키텍처 적용
    - 같은 유저의 기기들은 이미 접속한 채팅 서버로 모아, 메시지를 서버 한 곳에만 보내면 되게 함

## 모니터링

**Grafana** (나중에 적용해보기)
