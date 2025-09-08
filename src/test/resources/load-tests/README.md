# 채팅 시스템 부하 테스트 가이드

이 문서는 채팅 시스템의 부하 테스트 방법을 안내합니다.

## 📋 테스트 종류

### 1. Spring Boot 통합 테스트 (Kotlin)

**파일**: `ChatLoadTest.kt`, `ChatPerformanceTest.kt`

#### 실행 방법
```bash
# 단일 부하 테스트
./gradlew test --tests "com.back.domain.chat.chat.ChatLoadTest.testChatSystemLoadWithStages"

# 성능 테스트 모음
./gradlew test --tests "com.back.domain.chat.chat.ChatPerformanceTest"

# 특정 테스트만 실행
./gradlew test --tests "com.back.domain.chat.chat.ChatPerformanceTest.testHighConcurrencyLoad"
```

#### 테스트 시나리오
- **단계별 부하 증가**: 20명 → 50명 → 100명 → 50명 → 10명
- **고부하 동시성**: 100명이 60초간 동시 접속
- **스파이크 테스트**: 10명 → 200명 → 10명
- **지속 부하**: 50명이 5분간 지속
- **메모리 누수 감지**: 반복 실행으로 메모리 패턴 분석

#### 성능 임계값
- 성공률: >= 95%
- P95 응답시간: <= 2초
- P99 응답시간: <= 5초
- 처리량: >= 10 req/s

### 2. k6 외부 부하 테스트 (JavaScript)

**파일**: `chat-load-test.js`

#### 실행 방법
```bash
# 기본 실행 (localhost:8080)
k6 run src/test/resources/load-tests/chat-load-test.js

# 다른 서버 대상
k6 run -e BASE_URL=https://your-server.com src/test/resources/load-tests/chat-load-test.js

# 결과를 JSON으로 출력
k6 run --out json=results.json src/test/resources/load-tests/chat-load-test.js

# InfluxDB에 메트릭 전송
k6 run --out influxdb=http://localhost:8086/k6 src/test/resources/load-tests/chat-load-test.js
```

#### 테스트 단계
1. **워밍업** (1분, 20명)
2. **부하 증가** (2분, 50명)
3. **최대 부하** (3분, 100명)
4. **부하 감소** (2분, 50명)
5. **마무리** (1분, 0명)

#### 측정 메트릭
- `chatroom_create_success`: 채팅방 생성 성공률 (>95%)
- `message_query_success`: 메시지 조회 성공률 (>95%)
- `chatroom_query_duration`: 채팅방 조회 응답시간 (P95 <1초)
- `message_query_duration`: 메시지 조회 응답시간 (P95 <2초)

## 🔧 테스트 환경 설정

### Spring Boot 테스트
```properties
# src/test/resources/application-test.properties
spring.profiles.active=test
spring.jpa.hibernate.ddl-auto=create-drop
spring.datasource.url=jdbc:h2:mem:testdb
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### k6 테스트 환경 변수
```bash
export BASE_URL=http://localhost:8080
export K6_WEB_DASHBOARD=true  # 웹 대시보드 활성화
```

## 👥 테스트 사용자 계정

테스트에서 사용되는 기본 계정들:

| 이메일 | 비밀번호 | 이름 |
|--------|----------|------|
| user1@user.com | user1234! | 유저1 |
| user2@user.com | user1234! | 유저2 |
| test1@user.com | 1234 | 김혁신 |
| test2@user.com | 1234 | 박기술 |

## 📊 결과 분석

### 성공 기준
✅ **PASS 조건**
- 채팅방 생성 성공률 > 95%
- 메시지 조회 성공률 > 95%
- P95 응답시간 < 2초
- P99 응답시간 < 5초

❌ **FAIL 조건**
- 임계값 중 하나라도 미달
- 메모리 사용량 50% 이상 증가 (누수 의심)
- 에러율 5% 초과

### 모니터링 포인트
1. **응답시간**: 평균, P95, P99 추이
2. **처리량**: 초당 처리 요청 수
3. **에러율**: HTTP 4xx, 5xx 비율
4. **메모리 사용량**: 테스트 진행 중 증가 패턴
5. **데이터베이스 커넥션**: 풀 사용량 모니터링

## 🚀 CI/CD 통합

### GitHub Actions 예시
```yaml
name: Performance Test
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  load-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run Load Tests
        run: ./gradlew test --tests "*ChatLoadTest*"
      - name: Install k6
        run: |
          sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
          echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
          sudo apt-get update
          sudo apt-get install k6
      - name: Run k6 Load Test
        run: k6 run src/test/resources/load-tests/chat-load-test.js
```

## 📈 성능 최적화 팁

### 데이터베이스
- 인덱스 최적화: `chatroom_id`, `created_at` 조합
- 커넥션 풀 튜닝: `spring.datasource.hikari.maximum-pool-size`
- 쿼리 최적화: N+1 문제 해결

### Redis
- 커넥션 풀 설정: `spring.data.redis.jedis.pool.max-active`
- 메시지 TTL 설정으로 메모리 사용량 제어
- Redis Cluster 고려

### JVM
- 힙 메모리: `-Xmx4g -Xms4g`
- GC 튜닝: `-XX:+UseG1GC`
- 프로파일링: `-XX:+FlightRecorder`

### 애플리케이션
- 비동기 처리: `@Async` 활용
- 캐싱 전략: `@Cacheable` 적용
- 커넥션 타임아웃 조정

## 🔍 트러블슈팅

### 자주 발생하는 문제

1. **로그인 실패**
   - 테스트 데이터베이스에 사용자 생성 확인
   - JWT 토큰 설정 확인

2. **채팅방 생성 실패**
   - 게시글 데이터 존재 여부 확인
   - 권한 설정 확인

3. **메모리 부족**
   - 힙 메모리 증설
   - 테스트 사용자 수 조절

4. **네트워크 타임아웃**
   - HTTP 클라이언트 타임아웃 증가
   - 서버 리소스 확인

### 로그 확인
```bash
# 애플리케이션 로그
tail -f logs/application.log

# 테스트 결과 상세
./gradlew test --info

# k6 상세 로그
k6 run --verbose src/test/resources/load-tests/chat-load-test.js
```
