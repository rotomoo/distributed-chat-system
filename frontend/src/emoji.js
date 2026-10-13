// 이모지 고르기에 쓰는 목록 (외부 라이브러리 없이 자주 쓰는 것만). k = 찾기용 한글 / 영어 낱말
export const EMOJI_GROUPS = [
  {
    name: '자주 쓰는',
    items: [
      ['👍', '좋아요 최고 따봉 like thumbs up yes'], ['❤️', '하트 사랑 좋아 love heart red'],
      ['😂', '웃김 웃음 눈물 ㅋㅋ joy laugh lol'], ['🙏', '감사 부탁 제발 기도 thanks please pray'],
      ['🎉', '축하 파티 짝짝 party tada congrats'], ['👀', '눈 보는중 확인 eyes look'],
      ['🔥', '불 대박 핫 fire hot lit'], ['✅', '완료 확인 체크 done check ok'],
      ['😊', '미소 웃음 행복 smile happy blush'], ['👏', '박수 짝짝 clap applause'],
      ['🙌', '만세 환호 yay raised hands'], ['💯', '백점 완벽 hundred perfect'],
    ],
  },
  {
    name: '표정',
    items: [
      ['😀', '웃음 미소 grin smile'], ['😃', '웃음 기쁨 smiley'], ['😄', '활짝 웃음 smile'], ['😁', '히죽 웃음 beam grin'],
      ['😆', '깔깔 웃음 laughing'], ['😅', '땀 머쓱 민망 sweat smile'], ['🤣', '데굴데굴 폭소 rofl'], ['🙂', '살짝 미소 slight smile'],
      ['😉', '윙크 wink'], ['😍', '반함 하트눈 heart eyes love'], ['🥰', '사랑 행복 love smiling hearts'], ['😘', '뽀뽀 키스 kiss'],
      ['😋', '맛있다 냠냠 yum delicious'], ['😎', '멋짐 선글라스 cool sunglasses'], ['🤔', '생각 고민 음 thinking hmm'], ['🤨', '의심 갸웃 raised eyebrow'],
      ['😐', '무표정 neutral'], ['😑', '할말없음 expressionless'], ['🙄', '어이없음 눈굴림 eye roll'], ['😏', '능글 smirk'],
      ['😮', '놀람 와 wow open mouth'], ['😲', '깜짝 astonished'], ['😳', '당황 부끄 flushed'], ['🥺', '부탁 글썽 pleading'],
      ['😢', '슬픔 눈물 cry sad'], ['😭', '엉엉 오열 sob'], ['😤', '씩씩 화남 huff'], ['😡', '화남 분노 angry rage'],
      ['😱', '공포 비명 scream'], ['😴', '졸림 잠 sleep'], ['🤯', '머리터짐 충격 mind blown'], ['🥳', '파티 축하 party'],
      ['😇', '천사 착함 angel innocent'], ['🤗', '포옹 안아줘 hug'], ['🤐', '입다물기 비밀 zipper'], ['😬', '이크 어색 grimace'],
    ],
  },
  {
    name: '손',
    items: [
      ['👎', '싫어요 별로 dislike thumbs down no'], ['👌', '오케이 좋아 ok'], ['✌️', '브이 승리 victory peace'], ['🤞', '행운 기원 fingers crossed'],
      ['🤟', '사랑해 love you'], ['🤘', '락 rock'], ['👋', '안녕 인사 hi bye wave'], ['🤝', '악수 협력 handshake deal'],
      ['✋', '손 멈춰 손들기 hand stop'], ['👆', '위 이거 point up'], ['👇', '아래 point down'], ['👉', '오른쪽 이거 point right'],
      ['👈', '왼쪽 point left'], ['💪', '힘 화이팅 muscle strong'], ['✍️', '작성 쓰기 write'], ['🫡', '경례 충성 salute'],
      ['🫶', '하트손 사랑 heart hands'], ['🤙', '전화해 call me'],
    ],
  },
  {
    name: '하트',
    items: [
      ['🧡', '주황 하트 orange heart'], ['💛', '노란 하트 yellow heart'], ['💚', '초록 하트 green heart'], ['💙', '파란 하트 blue heart'],
      ['💜', '보라 하트 purple heart'], ['🖤', '검은 하트 black heart'], ['🤍', '흰 하트 white heart'], ['💔', '상처 이별 broken heart'],
      ['💕', '두근 하트 two hearts'], ['💖', '반짝 하트 sparkling heart'], ['💯', '백점 hundred'], ['✨', '반짝 sparkles'],
    ],
  },
  {
    name: '음식',
    items: [
      ['☕', '커피 coffee'], ['🍵', '차 tea'], ['🍺', '맥주 beer'], ['🍻', '건배 cheers beers'],
      ['🍕', '피자 pizza'], ['🍔', '햄버거 burger'], ['🍜', '라면 국수 ramen noodle'], ['🍚', '밥 rice'],
      ['🍣', '초밥 sushi'], ['🍗', '치킨 chicken'], ['🍰', '케이크 cake'], ['🍩', '도넛 donut'],
      ['🍎', '사과 apple'], ['🥤', '음료 drink'],
    ],
  },
  {
    name: '사물',
    items: [
      ['🚀', '로켓 출시 배포 rocket launch ship'], ['💡', '아이디어 전구 idea'], ['📌', '고정 핀 pin'], ['📎', '클립 첨부 paperclip'],
      ['📝', '메모 노트 memo note'], ['📅', '일정 달력 calendar'], ['⏰', '알람 시간 alarm'], ['💻', '노트북 컴퓨터 laptop'],
      ['📱', '휴대폰 phone'], ['📊', '차트 그래프 chart'], ['🐛', '버그 bug'], ['🛠️', '도구 수리 tools fix'],
      ['🔒', '잠금 보안 lock'], ['🔔', '알림 종 bell'], ['📢', '공지 확성기 announce'], ['🎯', '목표 과녁 target goal'],
      ['🏆', '트로피 우승 trophy win'], ['🎁', '선물 gift'], ['☔', '비 우산 rain umbrella'], ['☀️', '해 맑음 sun'],
      ['🌙', '달 밤 moon night'], ['⭐', '별 star'], ['🌈', '무지개 rainbow'], ['🍀', '행운 클로버 luck clover'],
    ],
  },
  {
    name: '기호',
    items: [
      ['❌', '안돼 엑스 취소 no cross'], ['⭕', '맞음 동그라미 yes circle'], ['❗', '중요 느낌표 important'], ['❓', '질문 물음표 question'],
      ['⚠️', '경고 주의 warning'], ['🆗', '오케이 ok'], ['🆕', '새로운 new'], ['➕', '더하기 플러스 plus'],
      ['➖', '빼기 마이너스 minus'], ['🔄', '다시 반복 repeat'],
    ],
  },
]

// 찾기 : 낱말 일부만 맞아도 (대소문자 무시). 여러 묶음에 있는 이모지는 한 번만
export function searchEmoji(query) {
  const q = query.trim().toLowerCase()
  if (!q) return []
  const found = new Set()
  for (const group of EMOJI_GROUPS) {
    for (const [emoji, keywords] of group.items) {
      if (keywords.toLowerCase().includes(q)) found.add(emoji)
    }
  }
  return [...found]
}

export const RECENT_EMOJI_KEY = 'chat.recentEmoji'
export const RECENT_EMOJI_MAX = 16

// localStorage 를 못 쓰는 환경(사생활 보호 모드)에서는 최근 사용 없이 동작
export function readRecentEmoji() {
  try {
    const list = JSON.parse(localStorage.getItem(RECENT_EMOJI_KEY) ?? '[]')
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

// 방금 쓴 것을 맨 앞으로 (중복 없이, 최대 16개)
export function rememberEmoji(emoji) {
  const next = [emoji, ...readRecentEmoji().filter((e) => e !== emoji)].slice(0, RECENT_EMOJI_MAX)
  try {
    localStorage.setItem(RECENT_EMOJI_KEY, JSON.stringify(next))
  } catch {
    // 기억 못 해도 고르기는 된다
  }
  return next
}
