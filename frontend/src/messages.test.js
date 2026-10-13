import { describe, expect, it } from 'vitest'
import { mergeMessages, replaceReactions } from './messages.js'

const m = (messageId, message = messageId) => ({ messageId, channelId: 11, sendUserId: 1, message })

describe('[단위] mergeMessages', () => {
  it('success case : 같은 messageId 는 한 번만 남긴다', () => {
    // arrange
    const history = [m('6710a1f3c2e4b51a2c9d0e11'), m('6710a1f3c2e4b51a2c9d0e12')]
    const realtime = [m('6710a1f3c2e4b51a2c9d0e12'), m('6710a1f3c2e4b51a2c9d0e13')]

    // act
    const merged = mergeMessages(history, realtime)

    // assert
    expect(merged.map((x) => x.messageId)).toEqual([
      '6710a1f3c2e4b51a2c9d0e11',
      '6710a1f3c2e4b51a2c9d0e12',
      '6710a1f3c2e4b51a2c9d0e13',
    ])
  })

  it('success case : 들어온 순서와 상관없이 messageId(시간순) 오름차순으로 정렬한다', () => {
    // arrange
    const newer = [m('6710a1f3c2e4b51a2c9d0e20')]
    const older = [m('6710a1f3c2e4b51a2c9d0e05'), m('6710a1f3c2e4b51a2c9d0e10')]

    // act
    const merged = mergeMessages(newer, older)

    // assert
    expect(merged.map((x) => x.messageId)).toEqual([
      '6710a1f3c2e4b51a2c9d0e05',
      '6710a1f3c2e4b51a2c9d0e10',
      '6710a1f3c2e4b51a2c9d0e20',
    ])
  })

  it('success case : 둘 다 비어 있으면 빈 배열', () => {
    // arrange & act
    const merged = mergeMessages([], [])

    // assert
    expect(merged).toEqual([])
  })
})

describe('[단위] replaceReactions', () => {
  it('success case : 그 메시지의 리액션만 통째로 바꾼다', () => {
    // arrange
    const messages = [{ ...m('a'), reactions: [{ emoji: '👍', userIds: [1] }] }, { ...m('b'), reactions: [] }]

    // act
    const next = replaceReactions(messages, 'a', [{ emoji: '🎉', userIds: [2, 3] }])

    // assert
    expect(next[0].reactions).toEqual([{ emoji: '🎉', userIds: [2, 3] }])
    expect(next[1]).toBe(messages[1])
  })

  it('fail case : 불러온 범위에 없는 메시지면 목록을 그대로 돌려준다', () => {
    // arrange
    const messages = [m('a')]

    // act
    const next = replaceReactions(messages, 'zzz', [{ emoji: '👍', userIds: [1] }])

    // assert
    expect(next).toBe(messages)
  })
})
