import { describe, expect, it } from 'vitest'
import { PRESENCE_LABEL, base64UrlToBytes, channelFromUrl, documentTitle, messageNotification } from './notify.js'

const CHANNEL = { channelId: 11, kind: 'channel', name: '점심메뉴' }
const DM = { channelId: 13, kind: 'dm', name: '영희' }

describe('[단위] messageNotification', () => {
  it('success case : 채널은 "#채널명", 본문은 "보낸사람: 미리보기", tag 는 channel-{id}', () => {
    // arrange & act
    const shown = messageNotification({ channel: CHANNEL, senderName: '영희', message: { message: ' 국밥 어때요? ' } })

    // assert
    expect(shown).toEqual({ title: '#점심메뉴', body: '영희: 국밥 어때요?', tag: 'channel-11' })
  })

  it('success case : DM 은 상대 이름이 제목, 파일만 있으면 "파일 N개"', () => {
    // arrange & act
    const shown = messageNotification({ channel: DM, senderName: '영희', message: { message: '', attachments: [{}, {}] } })

    // assert
    expect(shown).toEqual({ title: '영희', body: '영희: 파일 2개', tag: 'channel-13' })
  })

  it('success case : 미리보기는 100자까지', () => {
    // arrange & act
    const shown = messageNotification({ channel: CHANNEL, senderName: '민수', message: { message: 'ㄱ'.repeat(150) } })

    // assert
    expect(shown.body).toBe(`민수: ${'ㄱ'.repeat(100)}`)
  })
})

describe('[단위] documentTitle / channelFromUrl / base64UrlToBytes / PRESENCE_LABEL', () => {
  it('success case : 안 읽은 메시지가 있으면 "(N) 팀이름", 없으면 팀이름', () => {
    // arrange & act
    const shown = [documentTitle('60hz', 3), documentTitle('60hz', 0), documentTitle(undefined, 0)]

    // assert
    expect(shown).toEqual(['(3) 60hz', '60hz', 'Distributed Chat'])
  })

  it('success case : 주소의 ?channel= 숫자만 채널 id 로 읽는다', () => {
    // arrange & act
    const read = [channelFromUrl('/?channel=12'), channelFromUrl('http://localhost:5173/?channel=7&x=1'), channelFromUrl('/?channel=abc'), channelFromUrl('/')]

    // assert
    expect(read).toEqual([12, 7, null, null])
  })

  it('success case : base64url 공개키를 바이트로 바꾼다 (- _ 와 = 패딩 처리)', () => {
    // arrange
    const bytes = new Uint8Array([251, 255, 191, 0, 1])
    const base64Url = btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')

    // act
    const decoded = base64UrlToBytes(base64Url)

    // assert
    expect(base64Url).toContain('-')
    expect([...decoded]).toEqual([...bytes])
  })

  it('success case : 접속 상태 문구', () => {
    // arrange & act & assert
    expect(PRESENCE_LABEL).toEqual({ online: '● 온라인', away: '◐ 자리 비움', offline: '○ 오프라인' })
  })
})
