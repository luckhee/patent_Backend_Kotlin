import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 채팅 도메인 전용 메트릭
const chatRoomCreateRate = new Rate('chatroom_create_success');
const messageQueryRate = new Rate('message_query_success');
const chatRoomQueryDuration = new Trend('chatroom_query_duration');
const messageQueryDuration = new Trend('message_query_duration');

export const options = {
  stages: [
    { duration: '1m', target: 20 },    // 워밍업
    { duration: '2m', target: 50 },    // 부하 증가
    { duration: '3m', target: 100 },   // 최대 부하
    { duration: '2m', target: 50 },    // 부하 감소
    { duration: '1m', target: 0 },     // 마무리
  ],
  thresholds: {
    chatroom_create_success: ['rate>0.95'],        // 95% 성공률
    message_query_success: ['rate>0.95'],          // 95% 성공률
    chatroom_query_duration: ['p(95)<1000'],       // 95%ile < 1초
    message_query_duration: ['p(95)<2000'],        // 95%ile < 2초
  },
};

// 환경 변수로 베이스 URL 설정 가능 (기본값: localhost)
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 기본 사용자 (실제 로그인용)
const BASE_USERS = [
  { email: 'user1@user.com', password: 'user1234!', name: '유저1' },
  { email: 'user2@user.com', password: 'user1234!', name: '유저2' },
  { email: 'test1@user.com', password: '1234', name: '김혁신' },
  { email: 'test2@user.com', password: '1234', name: '박기술' },
];

export function setup() {
  console.log(`🚀 채팅 시스템 부하 테스트 시작 - 대상: ${BASE_URL}`);
  return { baseUsers: BASE_USERS };
}

export default function(data) {
  // 각 VU가 완전히 다른 사용자와 게시글 조합 사용 (겹치지 않게)
  const userIndex = (__VU - 1) % data.baseUsers.length;
  const baseUser = data.baseUsers[userIndex];
  const postId = ((__VU - 1) % 2) + 1; // 게시글 1-2번
  const uniqueId = __VU; // VU별 고유 식별자

  console.log(`[VU ${__VU}] ${baseUser.name} - 게시글 ${postId} 독립 세션`);

  // === 각 VU마다 독립적인 로그인 ===
  const loginResponse = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    email: baseUser.email,
    password: baseUser.password
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  if (loginResponse.status !== 200) {
    console.error(`[${baseUser.name}] 로그인 실패:`, loginResponse.body);
    return;
  }

  const loginData = JSON.parse(loginResponse.body);
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${loginData.data.accessToken}`
  };

  const userId = loginData.data.memberInfo.id;

  // === 1. 채팅방 생성 ===
  const createResponse = http.post(`${BASE_URL}/api/chat/rooms/${postId}`, null, { headers });
  
  let createSuccess = check(createResponse, {
    '채팅방 생성 성공': (r) => r.status === 200,
  });

  chatRoomCreateRate.add(createSuccess);

  if (!createSuccess) {
    console.error(`[${baseUser.name}] 채팅방 생성 실패:`, createResponse.status, createResponse.body);
    return;
  }

  const chatRoomId = JSON.parse(createResponse.body).data;
  console.log(`[${baseUser.name}] 채팅방 ${chatRoomId} 생성됨`);

  // === 2. 내 채팅방 목록 조회 부하 (5회) ===
  for (let i = 1; i <= 5; i++) {
    const queryStart = Date.now();
    const myRoomsResponse = http.get(`${BASE_URL}/api/chat/rooms/my`, { headers });
    chatRoomQueryDuration.add(Date.now() - queryStart);
    
    let roomQuerySuccess = check(myRoomsResponse, {
      '내 채팅방 목록 조회 성공': (r) => r.status === 200,
    });

    if (roomQuerySuccess) {
      const rooms = JSON.parse(myRoomsResponse.body).data;
      console.log(`[${baseUser.name}] 채팅방 ${rooms.length}개 조회 (${i}/5)`);
    } else {
      console.error(`[${baseUser.name}] 채팅방 목록 조회 실패:`, myRoomsResponse.status);
    }

    sleep(0.2);
  }

  // === 3. 내가 참여한 채팅방 메시지 조회 부하 (8회) ===
  for (let i = 1; i <= 8; i++) {
    const msgQueryStart = Date.now();
    const messagesResponse = http.get(`${BASE_URL}/api/chat/rooms/${chatRoomId}/messages`, { headers });
    messageQueryDuration.add(Date.now() - msgQueryStart);
    
    let msgSuccess = check(messagesResponse, {
      '메시지 조회 성공': (r) => r.status === 200,
    });

    messageQueryRate.add(msgSuccess);

    if (msgSuccess) {
      const messages = JSON.parse(messagesResponse.body).data;
      console.log(`[${baseUser.name}] 메시지 ${messages.length}개 조회 (${i}/8)`);
    } else {
      console.error(`[${baseUser.name}] 메시지 조회 실패:`, messagesResponse.status, messagesResponse.body);
    }

    sleep(0.2);
  }

  // === 4. 채팅방 나가기 (타이밍 분산) ===
  const randomDelay = Math.random() * 5; // 0-5초 랜덤 지연
  sleep(randomDelay);
  
  const leaveResponse = http.del(`${BASE_URL}/api/chat/rooms/${chatRoomId}`, null, { headers });
  let leaveSuccess = check(leaveResponse, {
    '채팅방 나가기 성공': (r) => r.status === 200 || r.status === 404, // 404도 성공으로 간주 (이미 삭제됨)
  });

  if (leaveResponse.status === 404) {
    console.log(`[${baseUser.name}] 채팅방 이미 삭제됨 (다른 사용자가 먼저 나감)`);
  } else if (!leaveSuccess) {
    console.error(`[${baseUser.name}] 채팅방 나가기 실패:`, leaveResponse.status, leaveResponse.body);
  } else {
    console.log(`[${baseUser.name}] 채팅방 ${chatRoomId} 나가기 완료`);
  }

  sleep(1);
}

export function teardown(data) {
  console.log('🏁 채팅 시스템 부하 테스트 완료');
  console.log('📊 메트릭 요약:');
  console.log('  - chatroom_create_success: 채팅방 생성 성공률');
  console.log('  - message_query_success: 메시지 조회 성공률');
  console.log('  - chatroom_query_duration: 채팅방 조회 응답시간');
  console.log('  - message_query_duration: 메시지 조회 응답시간');
}
