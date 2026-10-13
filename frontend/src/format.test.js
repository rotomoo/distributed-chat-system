import { describe, expect, it } from 'vitest'
import {
  AVATAR_COLORS, avatarColor, badgeText, buildTimeline, fileKind, formatDay, formatSize, formatTime, initials, isImage, teamInitials,
} from './format.js'

// 2026-10-13(화) 기준
const at = (month, day, hour, minute = 0, second = 0) => new Date(2026, month - 1, day, hour, minute, second).getTime()
const NOW = at(10, 13, 18)
const id = (n) => n.toString(16).padStart(24, '0')
const msg = (n, sendUserId, publishDt) => ({ messageId: id(n), channelId: 11, sendUserId, message: `msg ${n}`, publishDt })
const kinds = (items) => items.map((item) => (item.type === 'message' ? `${item.continued ? '+' : 'M'}${parseInt(item.key, 16)}` : item.type))

describe('[단위] formatTime', () => {
  it('success case : 오전 / 오후 12시간제로 보여 준다', () => {
    // arrange
    const noon = at(10, 13, 12, 1)
    const morning = at(10, 13, 9, 5)
    const midnight = at(10, 13, 0, 30)

    // act
    const shown = [formatTime(noon), formatTime(morning), formatTime(midnight)]

    // assert
    expect(shown).toEqual(['오후 12:01', '오전 9:05', '오전 12:30'])
  })
})

describe('[단위] formatDay', () => {
  it('success case : 오늘 / 어제 / 그 전은 월 일 요일', () => {
    // arrange
    const today = at(10, 13, 9)
    const yesterday = at(10, 12, 23, 59)
    const older = at(10, 5, 10)

    // act
    const shown = [formatDay(today, NOW), formatDay(yesterday, NOW), formatDay(older, NOW)]

    // assert
    expect(shown).toEqual(['오늘', '어제', '10월 5일 월요일'])
  })

  it('success case : 다른 해면 연도를 붙인다', () => {
    // arrange
    const lastYear = new Date(2025, 9, 13, 10).getTime()

    // act
    const shown = formatDay(lastYear, NOW)

    // assert
    expect(shown).toBe('2025년 10월 13일 월요일')
  })
})

describe('[단위] buildTimeline', () => {
  it('success case : 같은 사람이 5분 안에 이어 보낸 메시지는 묶는다', () => {
    // arrange
    const messages = [
      msg(1, 2, at(10, 13, 12, 0)),
      msg(2, 2, at(10, 13, 12, 4, 59)),
      msg(3, 3, at(10, 13, 12, 5)),
      msg(4, 3, at(10, 13, 12, 6)),
    ]

    // act
    const items = buildTimeline(messages, { now: NOW })

    // assert
    expect(kinds(items)).toEqual(['day', 'M1', '+2', 'M3', '+4'])
  })

  it('fail case : 같은 사람이어도 앞 메시지와 5분 이상 벌어지면 새로 시작한다', () => {
    // arrange
    const messages = [msg(1, 2, at(10, 13, 12, 0)), msg(2, 2, at(10, 13, 12, 5))]

    // act
    const items = buildTimeline(messages, { now: NOW })

    // assert
    expect(kinds(items)).toEqual(['day', 'M1', 'M2'])
  })

  it('success case : 날이 바뀌면 날짜 구분선을 넣고 묶음도 끊는다', () => {
    // arrange
    const messages = [msg(1, 2, at(10, 12, 23, 58)), msg(2, 2, at(10, 13, 0, 1))]

    // act
    const items = buildTimeline(messages, { now: NOW })

    // assert
    expect(kinds(items)).toEqual(['day', 'M1', 'day', 'M2'])
    expect(items.filter((item) => item.type === 'day').map((item) => item.label)).toEqual(['어제', '오늘'])
  })

  it('success case : 첫 안 읽은 메시지 바로 앞에 "새 메시지" 구분선을 넣고 묶음을 끊는다', () => {
    // arrange
    const messages = [msg(1, 2, at(10, 13, 12, 0)), msg(2, 2, at(10, 13, 12, 1)), msg(3, 2, at(10, 13, 12, 2))]

    // act
    const items = buildTimeline(messages, { firstUnreadMessageId: id(2), now: NOW })

    // assert
    expect(kinds(items)).toEqual(['day', 'M1', 'unread', 'M2', '+3'])
  })

  it('fail case : 첫 안 읽은 메시지가 목록에 없으면 구분선을 넣지 않는다', () => {
    // arrange
    const messages = [msg(5, 2, at(10, 13, 12, 0))]

    // act
    const items = buildTimeline(messages, { firstUnreadMessageId: id(1), now: NOW })

    // assert
    expect(kinds(items)).toEqual(['day', 'M5'])
  })
})

describe('[단위] avatarColor', () => {
  it('success case : 같은 userId 는 언제나 같은 색, 팔레트 안의 색이다', () => {
    // arrange
    const userIds = [1, 2, 3, 285, 286, 287]

    // act
    const first = userIds.map(avatarColor)
    const second = userIds.map(avatarColor)

    // assert
    expect(second).toEqual(first)
    first.forEach((color) => expect(AVATAR_COLORS).toContain(color))
  })

  it('success case : 사람마다 색이 갈린다', () => {
    // arrange
    const userIds = [1, 2, 3, 4, 5]

    // act
    const colors = new Set(userIds.map(avatarColor))

    // assert
    expect(colors.size).toBeGreaterThan(1)
  })
})

describe('[단위] initials / teamInitials / badgeText', () => {
  it('success case : 사람은 이름 첫 글자, 팀은 앞 두 글자(여러 단어면 단어 앞글자)', () => {
    // arrange & act
    const shown = [initials('영희'), initials('bison'), teamInitials('60hz'), teamInitials('Distributed Chat'), teamInitials('개발팀')]

    // assert
    expect(shown).toEqual(['영', 'B', '60', 'DC', '개발'])
  })

  it('success case : 배지는 100 이상이면 99+', () => {
    // arrange & act
    const shown = [badgeText(3), badgeText(99), badgeText(100)]

    // assert
    expect(shown).toEqual(['3', '99', '99+'])
  })
})

describe('[단위] formatSize / fileKind / isImage', () => {
  it('success case : 크기는 B / KB / MB 로 소수 한 자리까지 (.0 은 뗀다)', () => {
    // arrange & act
    const shown = [formatSize(12), formatSize(2048), formatSize(1_258_291), formatSize(20 * 1024 * 1024)]

    // assert
    expect(shown).toEqual(['12B', '2KB', '1.2MB', '20MB'])
  })

  it('success case : 파일 아이콘은 확장자나 형식으로 고른다', () => {
    // arrange & act
    const labels = [
      fileKind('견적서.pdf', 'application/pdf'), fileKind('data.XLSX', ''), fileKind('기획.hwp', 'application/octet-stream'),
      fileKind('녹화', 'video/mp4'), fileKind('noext', 'application/octet-stream'),
    ].map((kind) => kind.label)

    // assert
    expect(labels).toEqual(['PDF', 'XLS', 'DOC', 'MP4', 'FILE'])
  })

  it('success case : image/* 만 이미지로 본다', () => {
    // arrange & act
    const shown = [isImage('image/png'), isImage('application/pdf'), isImage(undefined)]

    // assert
    expect(shown).toEqual([true, false, false])
  })
})
