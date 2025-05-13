# Distributed Chat System

## 사용법

```markdown
1. docker-compose up --build

2. visit localhost:8080
```

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
    - 📁 distributed-chat-kafka-consumer
    - 📁 distributed-chat-kafka-producer
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

  Shared Session Store - Redis 기반 세션 중앙화


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
    - 자동 Failover : 가용성 확보

<br>

**MongoDB**

- 컬렉션 설계
- 분산 처리
    - 샤딩 키 : ChannelId

## 채팅 메시지 흐름

**Client-Server 양방향 통신 [Web Socket 프로토콜](https://rotomoo.tistory.com/100)**

<br>

## 세션 관리

<br>

## 캐시 계층

**Redis [Look Aside + Write Around 전략](https://rotomoo.tistory.com/99)**
![caching-strategies](./image/caching-strategies.png)
**CDN 적용**

## 모니터링

**Grafana**