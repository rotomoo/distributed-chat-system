# Distributed Chat System

## 데모

**http://15.165.80.205:8080** (AWS EC2, 고정 IP)

- 회원가입 → 로그인 → `Connect` → 메시지 전송. 다른 브라우저(시크릿 창)에서 다른 계정으로 접속하면 서로 메시지가 오간다.
- 크레딧 절약을 위해 서버는 평소 꺼져 있다. 접속이 안 되면 서버를 켠다 (켜고 배포까지 약 3~5분):
  - GitHub → Actions → `Server` → Run workflow → `start`
  - 또는 `gh workflow run server.yml -f action=start`
- 매일 02:00 KST 에 자동으로 꺼진다. 다 쓰면 `stop` 으로 바로 꺼도 된다.

## 사용법

```markdown
1. cp .env.example .env   # 비밀값 채우기 (.env 는 git 미추적)

2. docker compose up -d --build

3. visit localhost:8080
```

- IDE 에서 서비스를 직접 띄울 때는 `DB_USERNAME`, `DB_PASSWORD`, `jasypt_encryptor_password` 를 실행 환경변수로 넣는다.

**프론트엔드 (frontend/ — Vite + React)**

- 빌드 산출물은 api-gateway 의 `src/main/resources/static` 으로 떨어지고(git 미추적), 게이트웨이가 `:8080` 에서 화면과 API 를 같은 origin 으로 내보낸다.
- `./gradlew build` 시 api-gateway 의 `processResources` 가 npm 빌드를 함께 돌린다. npm 이 없으면 `SKIP_FRONTEND=true` 로 건너뛴다.
- 개발 중에는 `cd frontend && npm run dev` → `localhost:5173` (`/v1`, `/websocket` 은 `:8080` 게이트웨이로 프록시)

## CI/CD (GitHub Actions → AWS)

| 워크플로 | 언제 | 하는 일 |
|---|---|---|
| `CI` | PR, main 외 브랜치 push | 빌드 + 프론트 빌드 + 테스트 (mysql, mongodb 서비스 컨테이너) |
| `Deploy` | main 머지 | CI → 서비스 6종 이미지 ECR push → 서버가 켜져 있으면 SSM 으로 배포 |
| `Server` | 수동(start/stop), 매일 02:00 KST | 서버 켜고 main 최신으로 배포 / 서버 끄기 (크레딧 절약) |

- 서버 : EC2 `chat-server` (m7i-flex.large, ap-northeast-2), `http://15.165.80.205:8080`
- 인증 : GitHub OIDC → IAM 역할 `chat-github-actions` (액세스 키 없음, main 브랜치만 허용)
- 배포 : SSM Run Command 가 서버의 `/opt/chat` 에서 `deploy/deploy.sh` 실행 (`docker-compose.yml` + `deploy/docker-compose.prod.yml`)
- 서버에서 직접 : `ssh -i ~/.ssh/chat-server ubuntu@15.165.80.205` → `cd /opt/chat && sudo bash deploy/deploy.sh`
- 서버 비밀값 : `/opt/chat/.env` (서버에서 직접 작성, 레포에 없음)

## 모듈 계층

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
    - 📁 distributed-chat-system-connection-status
    - 📁 distributed-chat-system-kafka-consumer
    - 📁 distributed-chat-system-kafka-producer
    - 📁 distributed-chat-system-notification

```
distributed-chat-system
  ├── common    # 1 depth - 공통 기능 모듈
  ├── infra     # 2 depth - 인프라 관련 모듈
  └             # 3 depth - 프로젝트 모듈
```

## 아키텍처 설계

![architecture](./image/architecture.png)

# 설계과정

## 기능 요구사항

- 팀 기능
- 실시간 DM(Direct Message), 채널 채팅 기능
- 실시간 팀 사용자 접속상태 표시 기능
- DAU(Daily Active User) 10,000,000명 지원 시스템
- 멘션 기능
- 채팅 메시지 첨부파일(이미지) 지원 기능
- 채팅 메시지 이모티콘 반응 기능
- 채팅 메시지 댓글 기능
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
          "type": "image",
          "url": "https://s3.amazonaws.com/bucket/file_001.jpg"
        }
      ]
    }
    ```
  <br>
- Redis

  Shared Session Store - Redis 기반 세션 중앙화  <br>
  [Redis Sentinel (Failover : SPOF 방지, 가용성 확보)](https://rotomoo.tistory.com/101)


- S3

  이미지 첨부파일을 저장하고, 파일 URL만 MongoDB에 저장.

<br>

**저장소 요구량**

- DAU (Daily Active Users): 10,000,000명
- 1인당 일일 평균 메시지 전송 수: 20건
- 최대 텍스트 메시지 크기: 200KB (한글, 100,000자 제한)
- 이미지 첨부파일 비율: 20%
- 평균 이미지 크기: 200KB

- 연간 메시지 크기 = 10,000,000 * 20건 X 200KB * 365일 = 14.6PB / year (14,600TB / year)
- 연간 이미지 크기 = 10,000,000 * 20건 * 20% * 200KB * 365일 = 2.92PB / year (2,920TB / year)

- 10년간 MongoDB 저장소 요구량 146 PB
    - 분산(샤딩)

- 10년간 S3 저장소 요구량 = 29 PB
    - S3 분산

<br>

**채팅서버 메모리 요구량**

- 동시 접속자: 1,000,000명
- 접속당 서버 메모리 : 10KB
- 채팅서버 메모리 요구량 : 10GB
    - 트래픽 분산 필요 : 채팅 서버 분산
        - SPOF 방지 : 가용성 확보

<br>

## 기능 목록

**(infra) api-gateway server**

- 가용 서버 라우팅 (spring-cloud-gateway)
    - 기존 접속 채팅 서버 우선 라우팅
    - 채팅 서버
      로드밸런서 [(spring-cloud-loadbalancer, round-robin)](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/loadbalancer.html)

<br>

**(infra) service-discovery server**

- client-side discovery (spring-cloud-eureka)
    - 가용 서버 정보 동기화

<br>

**chatting server**

- 메시지 전송
- 메시지 수신

<br>

**client-api server**

- 회원가입
- 로그인
- 팀 목록 조회
- 멘션 회원 목록 조회

<br>

**connection-status server**

- 팀 사용자 접속상태 박동(heartbeat) 검사
- 팀 사용자 접속상태 목록 조회

<br>

**kafka-consumer server**

- 채팅 메시지 전송 이벤트 소비

<br>

**kafka-producer server**

- 채팅 메시지 전송 이벤트 발행

<br>

**notification server**

- 미수신 메시지 푸시 (웹 푸시 + 안읽은 메시지 수 cnt)

<br>

## DB 설계

**Mysql**

- 도메인 모델 분석
  ![rdb_domain](./image/rdb_domain.png)
- 테이블 설계
  ![rdb_table](./image/rdb_table.png)
- 분산 처리
    - Replication : 주(master) - 쓰기 연산, 부(slave) - 읽기 연산
    - Failover : 가용성 확보

<br>

**MongoDB**

- 컬렉션 설계
- 분산 처리
    - 샤딩 키 : ChannelId

## 워크스페이스 / 채널 / DM (슬랙 구조)

| 개념 | 저장 | 규칙 |
|---|---|---|
| 워크스페이스 | `team`, `team_user` | 만든 사람이 첫 멤버가 되고 `#general` 이 함께 생긴다. 가입하면 `#general` 에 자동 참여 |
| 공개 채널 | `channel` (`channelType = PUBLIC`), `channel_user` | 워크스페이스 멤버는 목록을 볼 수 있고, **참여해야** 메시지를 받는다 |
| DM | `channel` (`channelType = DM`, 이름 `dm:{작은 userId}:{큰 userId}`) | 같은 워크스페이스 멤버끼리. 두 사람의 DM 은 하나뿐이라 다시 열면 기존 채널 |
| 채널 멤버 캐시 | Redis `channel:{channelId}:members` (Set) | 원본은 MySQL. DB 커밋 후에 넣는다. 채널 단위 전달(`SINTER`)과 발송 권한 확인에 쓴다 |

| API | 설명 | 오류 |
|---|---|---|
| `POST /v1/private/api/team/team` | 워크스페이스 생성 (+ `#general`) | 2001 이미 있는 이름 |
| `GET /v1/private/api/team/teams/me` | 내 워크스페이스 목록 | |
| `POST /v1/private/api/team/{teamId}/join` | 워크스페이스 가입 (+ `#general` 참여) | 2002 없는 팀 |
| `GET /v1/private/api/team/{teamId}/members` | 멤버 목록 | 2003 멤버 아님 |
| `GET /v1/private/api/channel/channels?teamId=` | 공개 채널 목록 (`joined` 로 참여 여부) | 2003 |
| `POST /v1/private/api/channel/channel?teamId=` | 공개 채널 생성 (만든 사람 참여) | 3001 이미 있는 이름, 2003 |
| `POST /v1/private/api/channel/{channelId}/join` | 공개 채널 참여 | 3002 없는 채널, 2003 |
| `GET /v1/private/api/channel/dms?teamId=` | 내 DM 목록 | 2003 |
| `POST /v1/private/api/channel/dm?teamId=` | DM 열기 (없으면 생성) | 3004 자기 자신, 2003 상대가 멤버 아님 |

## 채팅 흐름

**Client-Server 양방향 통신 [Web Socket 프로토콜](https://rotomoo.tistory.com/100)**

> 아래 그림은 현재 코드 기준이다. 채널 기능 전이라 모든 메시지는 전체 채널(`channelId = 0`)로 발행되고, 접속한 전체 유저가 받는다.

### 도메인 스토리 : 채팅 메시지가 상대에게 닿기까지

**스토리 1. 접속 준비 (① ~ ④)**

```mermaid
flowchart LR
    sender(["👤 보내는 사람"])
    receiver(["👤 받는 사람"])
    clientApi[["🖥️ Client API"]]
    session[/"🔑 세션"/]
    chatA[["🖥️ 채팅 서버 A"]]
    chatB[["🖥️ 채팅 서버 B"]]
    conn[/"📍 접속 정보<br/>누가 어느 채팅 서버에"/]

    sender -- "① 로그인한다" --> clientApi
    clientApi -- "② 발급해 공유 저장소에 둔다" --> session
    sender -- "③ 세션으로 접속한다" --> chatA
    receiver -- "③ 세션으로 접속한다" --> chatB
    chatA -- "④ 남긴다" --> conn
    chatB -- "④ 남긴다" --> conn
```

**스토리 2. 메시지 전달 (⑤ ~ ⑫)**

```mermaid
flowchart LR
    sender(["👤 보내는 사람"])
    msg[/"✉️ 채팅 메시지"/]
    chatA[["🖥️ 채팅 서버 A"]]
    producer[["🖥️ Kafka Producer"]]
    event[/"📨 메시지 이벤트"/]
    consumer[["🖥️ Kafka Consumer"]]
    history[/"🗂️ 메시지 이력"/]
    conn[/"📍 접속 정보"/]
    chatB[["🖥️ 채팅 서버 B"]]
    receiver(["👤 받는 사람"])

    sender -- "⑤ 보낸다" --> msg
    msg -- "⑤ 에게" --> chatA
    chatA -- "⑥ 발행을 맡긴다" --> producer
    producer -- "⑦ 발행한다" --> event
    event -- "⑧ 꺼낸다" --> consumer
    consumer -- "⑨ 저장한다" --> history
    consumer -- "⑩ 확인한다" --> conn
    consumer -- "⑪ 전달한다" --> chatB
    chatB -- "⑫ 보여준다" --> receiver
```

| # | 문장 |
|---|---|
| ① | 보내는 사람이 계정으로 **Client API** 에 로그인한다. |
| ② | Client API 가 **세션**을 발급해 공유 저장소(Redis)에 둔다. 어느 서버든 같은 세션을 읽는다. |
| ③ | 보내는 사람과 받는 사람이 세션으로 각자 **채팅 서버**에 접속한다. 두 사람이 서로 다른 서버에 붙을 수 있다. |
| ④ | 채팅 서버가 **접속 정보**(누가 어느 채팅 서버에 붙었는지)를 Redis 에 남긴다. |
| ⑤ | 보내는 사람이 **채팅 메시지**를 자기가 붙은 채팅 서버 A 에 보낸다. |
| ⑥ | 채팅 서버 A 는 다른 서버를 직접 부르지 않고 **Kafka Producer** 에 발행을 맡긴다. |
| ⑦ | Kafka Producer 가 **메시지 이벤트**를 Kafka 에 발행한다. |
| ⑧ | **Kafka Consumer** 가 메시지 이벤트를 꺼낸다. Consumer 가 잠시 죽어 있어도 이벤트는 Kafka 에 남는다. |
| ⑨ | Kafka Consumer 가 메시지를 **메시지 이력**(MongoDB)에 저장한다. |
| ⑩ | Kafka Consumer 가 **접속 정보**로 받는 사람들이 어느 채팅 서버에 있는지 확인한다. |
| ⑪ | Kafka Consumer 가 채팅 서버마다 한 번씩 메시지를 전달한다. |
| ⑫ | 채팅 서버 B 가 받는 사람에게 메시지를 보여준다. (보낸 사람도 같은 경로로 돌아온 메시지를 본다) |

### ① 로그인과 세션 공유

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant GW as API Gateway
    participant API as Client API (n대)
    participant DB as MySQL
    participant RS as Redis (세션 저장소)

    U->>GW: 로그인 POST /v1/public/api/auth/login
    GW->>API: lb://CLIENT-API (Eureka 목록, round-robin)
    API->>DB: 계정 조회
    DB-->>API: User (BCrypt 비밀번호)
    API->>API: 비밀번호 검증
    API->>RS: 세션 저장 (sessions:{SESSION}, userId, 인증 정보)
    Note over API,RS: 로그인 성공 시 세션 만료 30일로 연장
    API-->>GW: 200 + Set-Cookie SESSION, userId
    GW-->>U: 200

    U->>GW: 내 정보 GET /v1/private/api/user/my-info (Cookie SESSION)
    GW->>API: 다른 Client API 인스턴스로 가도 된다
    API->>RS: 세션 조회
    RS-->>API: userId, 인증 정보
    API-->>U: 내 정보
```

### ② 웹소켓 접속 : 기존 채팅 서버 우선 라우팅

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant GW as API Gateway
    participant RD as Redis
    participant SD as Service Discovery
    participant C1 as Chatting-1
    participant C2 as Chatting-2

    C1->>SD: 서비스 등록 (IP, Port, chatting)
    C2->>SD: 서비스 등록 (IP, Port, chatting)
    GW->>SD: CHATTING 인스턴스 목록 (주기적 갱신)
    SD-->>GW: [Chatting-1, Chatting-2]

    U->>GW: ws /websocket (Cookie SESSION, userId)
    GW->>RD: GET {userId} (이전에 붙었던 채팅 서버)
    alt 이전 접속 기록 있음
        RD-->>GW: ws://Chatting-1
        GW->>C1: 그 서버로 라우팅 (ChattingRoutingFilter)
    else 첫 접속
        RD-->>GW: 없음
        GW->>C1: round-robin 으로 선택
    end
    C1->>RD: 세션 조회 (sessions:{SESSION})
    alt 세션 없음
        C1-->>U: 핸드셰이크 거절
    else 로그인 상태
        RD-->>C1: userId
        C1->>RD: SET {userId} = ws://Chatting-1
        C1->>RD: SADD chatServerUri:{Chatting-1} userId
        C1->>C1: 웹소켓 세션을 메모리에 보관
        C1-->>U: 연결 성공
    end
```

> **알려진 한계** : 같은 유저가 두 기기(PC, 폰)로 같은 채팅 서버에 붙으면, 서버 메모리가 유저당 세션 1개라 나중 연결이 앞 연결을 덮어써서 앞 기기가 메시지를 못 받는다. 다중 기기 지원으로 수정 예정.

### ③ 메시지 전송 → 저장 → 서버 간 전달

```mermaid
sequenceDiagram
    autonumber
    actor A as 보내는 사람
    participant C1 as Chatting-1
    participant P as Kafka Producer
    participant K as Kafka
    participant CS as Kafka Consumer
    participant M as MongoDB
    participant RD as Redis
    participant C2 as Chatting-2
    actor B as 받는 사람

    A->>C1: 메시지 (웹소켓)
    C1->>P: POST /publish/chat-send-message (protobuf, Feign + lb)
    P->>K: chat-send-message 토픽에 발행 (key = channelId)
    Note over P,K: 같은 채널은 같은 파티션 → 채널 안의 순서 보장
    K-->>CS: 이벤트 소비 (group chat-group-1)
    CS->>M: 메시지 이력 저장
    M-->>CS: messageId
    CS->>RD: SCAN chatServerUri:*
    CS->>RD: SMEMBERS chatServerUri:{서버} (서버별 접속 유저)
    par 채팅 서버마다 한 번씩
        CS->>C1: POST /internal/chat/deliver (protobuf, 수신자 목록)
        C1-->>A: 메시지 JSON (보낸 사람 화면에도 이 경로로 표시)
    and
        CS->>C2: POST /internal/chat/deliver (protobuf, 수신자 목록)
        C2-->>B: 메시지 JSON
    end
    Note over CS,C2: 한 채팅 서버로 전달이 실패해도 나머지 서버 전달은 계속한다
```

### ④ 채팅 서버 장애 : client-side discovery

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant GW as API Gateway
    participant SD as Service Discovery
    participant RD as Redis
    participant C1 as Chatting-1
    participant C2 as Chatting-2

    C1->>SD: 서비스 등록 (IP, Port, chatting)
    C2->>SD: 서비스 등록 (IP, Port, chatting)
    loop 주기적 Heartbeat
        C1->>SD: Heartbeat
        C2->>SD: Heartbeat
    end
    U->>C1: 웹소켓 연결 중 (Redis : {userId} = Chatting-1)

    Note over C1: Chatting-1 종료
    C1->>SD: 등록 해제 (cancel)
    SD->>RD: SMEMBERS chatServerUri:{Chatting-1}
    SD->>RD: DEL {userId} ... (Chatting-1 에 있던 유저들)
    SD->>RD: DEL chatServerUri:{Chatting-1}
    C1--xU: 웹소켓 끊김

    GW->>SD: CHATTING 인스턴스 목록 (주기적 갱신)
    SD-->>GW: [Chatting-2]
    U->>GW: 재접속 ws /websocket
    GW->>RD: GET {userId}
    RD-->>GW: 없음 (정리됨)
    GW->>C2: round-robin
    C2->>RD: SET {userId} = Chatting-2, SADD chatServerUri:{Chatting-2}
    C2-->>U: 연결 성공
```

> 로컬 검증 (2026-10-12) : Chatting-1 정상 종료 → Redis 정리 → 재접속 시 Chatting-2 로 연결 확인. 하트비트 누락(강제 종료) 경로는 아직 검증하지 않았다.

### ⑤ Redis 장애 : Sentinel failover

```mermaid
sequenceDiagram
    autonumber
    participant App as 앱 서비스 (Lettuce)
    participant S as Sentinel x3
    participant M as Redis master
    participant R1 as Replica-1
    participant R2 as Replica-2

    loop 감시
        S->>M: PING
        S->>R1: PING
        S->>R2: PING
    end
    M->>R1: 복제
    M->>R2: 복제
    App->>M: 쓰기 (세션, 접속 정보)
    App->>R1: 읽기 (ReadFrom REPLICA_PREFERRED)

    Note over M: master 다운
    S->>S: 10초 응답 없음 → 3대 중 2대 합의 (quorum 2)
    S->>R2: REPLICAOF NO ONE (새 master 로 승격)
    S->>R1: REPLICAOF Replica-2
    S-->>App: master 변경 알림 (+switch-master)
    App->>R2: 쓰기 재개

    Note over M: 예전 master 복구
    S->>M: REPLICAOF Replica-2 (replica 로 합류)
```

> 로컬 검증 (2026-10-12) : master 다운 후 약 12초 만에 승격. 전환 순간 진행 중이던 요청 1건이 실패하고 이후 정상. 예전 master 는 복구 시 replica 로 합류.

## 캐시 계층

**Redis [Look Aside + Write Around 전략](https://rotomoo.tistory.com/99)**
![caching-strategies](./image/caching-strategies.png)
**CDN 적용** (나중에 적용해보기)

## 요구사항 검증
- 슬랙, 디스코드 같은 대규모 트래픽 서비스를 경험해 보고자 DAU 1,000만 명 규모의 트래픽을 감당할 수 있는 채팅 시스템 아키텍처를 수립하고 데이터 아카이빙 전략을 설계
- 팀 단위 협업 시 개발 생산성을 높이고, 기능 변경과 확장이 용이한 구조를 만들기 위해 서비스별 책임이 명확히 분리된 멀티 모듈 구조를 구축하여 유지보수성과 테스트 효율성을 확보
- RDB로는 기하급수적으로 늘어나는 채팅 데이터의 쓰기/조회 부하를 감당하기 어렵다고 판단하여 샤딩이 가능한 MongoDB 기반으로 채널 단위 메시지 컬렉션을 설계하고, WebSocket 기반 실시간 1:1 및 채널 채팅을 구현
    - 카프카를 Stomp로 사용하는것보단 ‘메시지 전송’ 같은 이벤트를 발행해서 컨슈머에서 관리하는게 맞다고 생각함. 채팅 서버간 의존도 안하게 되어 느슨한 결합도됨.
- 각 채팅 서버 메모리에 독립적으로 존재하는 WebSocketSession간 통신을 위해 유저의 접속 서버 정보를{userId: serverUrl} 형식을 redis로, 서버내 접속한 유저들을 RedisSet으로 저장 https://github.com/rotomoo/distributed-chat-system/blob/723e314b9c29c084696eaedbbb743eed99e30c1f/distributed-chat-system-chatting/src/main/java/com/distributed/chat/system/chatting/base/handler/CustomTextWebSocketHandler.java#L20-L38
- 컨슈머 서버를 통해 채팅 서버 간 직접 연결 없이 메시지 전달 책임을 분리 https://github.com/rotomoo/distributed-chat-system/blob/723e314b9c29c084696eaedbbb743eed99e30c1f/distributed-chat-system-chatting/src/main/java/com/distributed/chat/system/chatting/base/handler/CustomTextWebSocketHandler.java#L40-L52
    - 컨슈머 서버에서 Redis에 저장되어있는 세션 데이터들을 통해 접속한 채팅서버로 메시지를 전달.
    - 이때 컨슈머 서버에서 해당 데이터를 MongoDB에 저장.
    - 실제 전달할 메시지는 채팅서버 메모리를 통해 전송, 이전 메시지는 채팅서버 접속시 MongoDB로 조회.
    어차피 채널 단위로 샤딩이 되어있고 가장 최근 데이터 몇개만 slice로 조회하면 성능도 괜찮을거라 생각됨.
- Spring Cloud를 도입하여 클라이언트 사이드 서비스 디스커버리 패턴 적용.
    - 서버 스케일 아웃, 채팅서버 라우팅 등의 확장에 유리한 아키텍처 적용 https://github.com/rotomoo/distributed-chat-system/blob/723e314b9c29c084696eaedbbb743eed99e30c1f/infra/distributed-chat-system-api-gateway/src/main/resources/application-api-gateway.yml#L12-L24
    - 사용자가 다른 세션으로 재접속하면, 메시지 수신을 위해 각 채팅 서버에 개별 전송 필요하니,
    기존 접속한 서버로 재접속하게 하여 https://github.com/rotomoo/distributed-chat-system/blob/723e314b9c29c084696eaedbbb743eed99e30c1f/infra/distributed-chat-system-api-gateway/src/main/java/com/distributed/chat/system/api/gateway/base/filter/ChattingRoutingFilter.java#L22-L56 Map으로 관리, 새로운 것으로 덮어씌우는 방식으로 설계
    이때 Thread-safe한 ConcurrentHashMap으로 관리하여 동시에 접속하여도 문제없도록 설계 https://github.com/rotomoo/distributed-chat-system/blob/723e314b9c29c084696eaedbbb743eed99e30c1f/distributed-chat-system-chatting/src/main/java/com/distributed/chat/system/chatting/base/handler/CustomTextWebSocketHandler.java#L20-L23
    - Eureka 인스턴스 종료 이벤트 감지 시, 해당 서버에 연결된 사용자의 Redis 세션 정보도 삭제 https://github.com/rotomoo/distributed-chat-system/blob/723e314b9c29c084696eaedbbb743eed99e30c1f/infra/distributed-chat-system-service-discovery/src/main/java/com/distributed/chat/system/service/discovery/base/listener/EurekaInstanceCanceledListener.java#L17-L42
- 서버간 메시지 포맷 불일치를 구글 프로토콜 버퍼(Protobuf)를 도입하여 포맷 일원화를 적용 https://github.com/rotomoo/distributed-chat-system/blob/723e314b9c29c084696eaedbbb743eed99e30c1f/common/distributed-chat-system-common/src/main/proto/eda_chat_message.proto#L1-L9

## 모니터링

**Grafana** (나중에 적용해보기)
