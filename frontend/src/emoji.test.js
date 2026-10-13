import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { EMOJI_GROUPS, RECENT_EMOJI_KEY, RECENT_EMOJI_MAX, readRecentEmoji, rememberEmoji, searchEmoji } from './emoji.js'

describe('[단위] emoji', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('success case : 자주 쓰는 / 표정 / 손 / 하트 / 사물 묶음에 100개 넘게 있다', () => {
    // arrange & act
    const names = EMOJI_GROUPS.map((g) => g.name)
    const count = new Set(EMOJI_GROUPS.flatMap((g) => g.items.map(([emoji]) => emoji))).size

    // assert
    expect(names).toEqual(expect.arrayContaining(['자주 쓰는', '표정', '손', '하트', '사물']))
    expect(count).toBeGreaterThanOrEqual(100)
  })

  it('success case : 한글 / 영어 낱말로 찾고, 여러 묶음에 있어도 한 번만', () => {
    // arrange & act
    const coffee = searchEmoji('커피')
    const thumbs = searchEmoji('THUMBS')
    const hundred = searchEmoji('백점')

    // assert
    expect(coffee).toEqual(['☕'])
    expect(thumbs).toEqual(['👍', '👎'])
    expect(hundred).toEqual(['💯'])
  })

  it('fail case : 빈 검색어는 아무것도 찾지 않는다', () => {
    // arrange & act
    const found = searchEmoji('  ')

    // assert
    expect(found).toEqual([])
  })

  it('success case : 최근 사용은 방금 쓴 것을 맨 앞에, 중복 없이 최대 16개 기억한다', () => {
    // arrange
    for (let i = 0; i < 20; i++) rememberEmoji(`e${i}`)

    // act
    rememberEmoji('e5')
    const recent = readRecentEmoji()

    // assert
    expect(recent[0]).toBe('e5')
    expect(recent).toHaveLength(RECENT_EMOJI_MAX)
    expect(recent.filter((e) => e === 'e5')).toHaveLength(1)
    expect(JSON.parse(localStorage.getItem(RECENT_EMOJI_KEY))).toEqual(recent)
  })

  it('fail case : localStorage 를 못 쓰거나 값이 깨져 있으면 최근 사용 없이 동작한다', () => {
    // arrange
    localStorage.setItem(RECENT_EMOJI_KEY, '{broken')
    const broken = readRecentEmoji()
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('SecurityError') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('SecurityError') })

    // act
    const remembered = rememberEmoji('👍')

    // assert
    expect(broken).toEqual([])
    expect(remembered).toEqual(['👍'])
    expect(readRecentEmoji()).toEqual([])
  })
})
