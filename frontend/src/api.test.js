import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createChannel, createTeam, getCatchUp, getChannelMessages, getChannels, getDms, getMyTeams, getTeamMembers,
  fileUrl, joinChannel, joinTeam, markRead, openDm, uploadFile,
} from './api.js'

// XMLHttpRequest 대역. 테스트가 진행률 / 응답 / 실패를 직접 흘려 넣는다
class FakeXhr {
  static last = null
  constructor() {
    this.upload = {}
    FakeXhr.last = this
  }
  open(method, url) {
    this.method = method
    this.url = url
  }
  send(body) {
    this.body = body
  }
  abort() {
    this.onabort()
  }
  respond(status, body) {
    this.status = status
    this.responseText = typeof body === 'string' ? body : JSON.stringify(body)
    this.onload()
  }
}

const ok = (data) => ({ ok: true, status: 200, json: async () => ({ code: 0, msg: 'ok', data, success: true }) })
const fail = (code, msg) => ({ ok: true, status: 200, json: async () => ({ code, msg, data: null, success: false }) })
const notFound = () => ({ ok: false, status: 404, json: async () => ({ status: 404, error: 'Not Found' }) })
const urls = () => fetch.mock.calls.map(([url]) => url)

describe('[단위] api', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('success case : 메시지 조회는 before / after / size 를 쿼리로 보내고 { list, hasNext } 를 돌려준다', async () => {
    // arrange
    const list = [{ messageId: 'a' }]
    fetch.mockResolvedValue(ok({ list, hasNext: true }))

    // act
    const older = await getChannelMessages(11, { before: '6710a1f3c2e4b51a2c9d0e11', size: 20 })
    await getChannelMessages(11, { after: '6710a1f3c2e4b51a2c9d0e11', size: 100 })
    await getChannelMessages(11, { size: 20 })

    // assert
    expect(urls()).toEqual([
      '/v1/private/api/channel/11/messages?before=6710a1f3c2e4b51a2c9d0e11&size=20',
      '/v1/private/api/channel/11/messages?after=6710a1f3c2e4b51a2c9d0e11&size=100',
      '/v1/private/api/channel/11/messages?size=20',
    ])
    expect(older).toEqual({ list, hasNext: true })
  })

  it('fail case : hasNext 가 없으면(이전 서버) 더 없는 것으로 본다', async () => {
    // arrange
    fetch.mockResolvedValue(ok({ list: [{ messageId: 'a' }] }))

    // act
    const result = await getChannelMessages(11, { size: 1 })

    // assert
    expect(result).toEqual({ list: [{ messageId: 'a' }], hasNext: false })
  })

  it('fail case : 메시지 조회가 실패하면 서버 메시지로 에러를 던진다 (채널 멤버 아님 등)', async () => {
    // arrange
    fetch.mockResolvedValue(fail(3003, '채널 멤버가 아닙니다.'))

    // act & assert
    await expect(getChannelMessages(11)).rejects.toThrow('채널 멤버가 아닙니다.')
  })

  it('success case : 내 팀 / 채널 / DM 목록은 data.list 를 돌려주고, unreadCount 가 없으면 0 으로 채운다', async () => {
    // arrange
    fetch
      .mockResolvedValueOnce(ok({ list: [{ teamId: 1, teamName: '60hz' }] }))
      .mockResolvedValueOnce(ok({ list: [{ channelId: 11, channelName: '점심메뉴', joined: true, unreadCount: 3 }, { channelId: 12, channelName: '운동', joined: false }] }))
      .mockResolvedValueOnce(ok({ list: [{ channelId: 13, userId: 2, userName: '영희' }] }))

    // act
    const teams = await getMyTeams()
    const channels = await getChannels(1)
    const dms = await getDms(1)

    // assert
    expect(urls()).toEqual([
      '/v1/private/api/team/teams/me',
      '/v1/private/api/channel/channels?teamId=1',
      '/v1/private/api/channel/dms?teamId=1',
    ])
    expect(teams).toEqual([{ teamId: 1, teamName: '60hz' }])
    expect(channels.map((c) => c.unreadCount)).toEqual([3, 0])
    expect(dms).toEqual([{ channelId: 13, userId: 2, userName: '영희', unreadCount: 0 }])
  })

  it('fail case : 목록 조회가 실패하면 빈 배열', async () => {
    // arrange
    fetch.mockResolvedValue(fail(3001, '팀 멤버가 아닙니다.'))

    // act
    const channels = await getChannels(1)

    // assert
    expect(channels).toEqual([])
  })

  it('success case : 팀 멤버는 online 이 true 일 때만 온라인으로 본다', async () => {
    // arrange
    fetch.mockResolvedValue(ok({ list: [{ userId: 1, userName: '철수', online: true }, { userId: 2, userName: '영희' }] }))

    // act
    const members = await getTeamMembers(1)

    // assert
    expect(urls()).toEqual(['/v1/private/api/team/1/members'])
    expect(members.map((m) => m.online)).toEqual([true, false])
  })

  it('fail case : 멤버 API 가 아직 없으면(404) 빈 배열', async () => {
    // arrange
    fetch.mockResolvedValue(notFound())

    // act
    const members = await getTeamMembers(1)

    // assert
    expect(members).toEqual([])
  })

  it('success case : 팀 가입 / 채널 참여는 POST 로 보낸다', async () => {
    // arrange
    fetch.mockResolvedValue(ok(null))

    // act
    await joinTeam(1)
    await joinChannel(11)

    // assert
    expect(fetch).toHaveBeenNthCalledWith(1, '/v1/private/api/team/1/join', { method: 'POST' })
    expect(fetch).toHaveBeenNthCalledWith(2, '/v1/private/api/channel/11/join', { method: 'POST' })
  })

  it('fail case : 채널 참여가 실패하면 서버 메시지로 에러를 던진다', async () => {
    // arrange
    fetch.mockResolvedValue(fail(3002, '존재하지 않는 채널입니다.'))

    // act & assert
    await expect(joinChannel(99)).rejects.toThrow('존재하지 않는 채널입니다.')
  })

  it('success case : 팀 / 채널 만들기와 DM 열기는 JSON 본문으로 보내고 data 를 돌려준다', async () => {
    // arrange
    fetch
      .mockResolvedValueOnce(ok({ teamId: 3, teamName: '새팀', defaultChannelId: 30 }))
      .mockResolvedValueOnce(ok({ channelId: 31, channelName: '점심메뉴', joined: true }))
      .mockResolvedValueOnce(ok({ channelId: 32, userId: 2, userName: '영희' }))

    // act
    const team = await createTeam('새팀')
    const channel = await createChannel(3, '점심메뉴')
    const dm = await openDm(3, 2)

    // assert
    expect(urls()).toEqual([
      '/v1/private/api/team/team',
      '/v1/private/api/channel/channel?teamId=3',
      '/v1/private/api/channel/dm?teamId=3',
    ])
    expect(fetch.mock.calls.map(([, options]) => JSON.parse(options.body))).toEqual([
      { teamName: '새팀' }, { channelName: '점심메뉴' }, { userId: 2 },
    ])
    expect([team.teamId, channel.channelId, dm.channelId]).toEqual([3, 31, 32])
  })

  it('success case : 읽음 처리는 PUT /channel/{id}/read 에 messageId 를 보낸다', async () => {
    // arrange
    fetch.mockResolvedValue(ok(null))

    // act
    await markRead(11, '6710a1f3c2e4b51a2c9d0e11')

    // assert
    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/v1/private/api/channel/11/read')
    expect(options.method).toBe('PUT')
    expect(JSON.parse(options.body)).toEqual({ messageId: '6710a1f3c2e4b51a2c9d0e11' })
  })

  it('success case : 캐치업은 data 를 그대로 돌려준다', async () => {
    // arrange
    const data = { unreadCount: 7, firstUnreadMessageId: 'a' }
    fetch.mockResolvedValue(ok(data))

    // act
    const result = await getCatchUp(11)

    // assert
    expect(urls()).toEqual(['/v1/private/api/channel/11/catch-up'])
    expect(result).toEqual(data)
  })

  it('fail case : 캐치업 API 가 없거나(404) 실패하면 에러를 던진다', async () => {
    // arrange
    fetch.mockResolvedValue(notFound())

    // act & assert
    await expect(getCatchUp(11)).rejects.toThrow()
  })

  // ── 파일 올리기 ─────────────────────────────────

  describe('uploadFile', () => {
    beforeEach(() => {
      vi.stubGlobal('XMLHttpRequest', FakeXhr)
    })

    afterEach(() => {
      vi.unstubAllGlobals()
    })

    it('success case : file 과 channelId 를 multipart 로 POST 하고, 진행률을 알리고, data 를 돌려준다', async () => {
      // arrange
      const file = new File(['hello'], '메뉴.png', { type: 'image/png' })
      const onProgress = vi.fn()

      // act
      const { promise } = uploadFile(11, file, onProgress)
      const xhr = FakeXhr.last
      xhr.upload.onprogress({ lengthComputable: true, loaded: 50, total: 200 })
      xhr.respond(200, { code: 0, data: { fileId: 'f1', name: '메뉴.png', contentType: 'image/png', size: 5 } })
      const data = await promise

      // assert
      expect([xhr.method, xhr.url]).toEqual(['POST', '/v1/private/api/file'])
      expect(xhr.body.get('file')).toBe(file)
      expect(xhr.body.get('channelId')).toBe('11')
      expect(onProgress).toHaveBeenCalledWith(0.25)
      expect(data.fileId).toBe('f1')
    })

    it('fail case : 서버가 실패 코드를 주면 그 메시지로 실패한다', async () => {
      // arrange
      const { promise } = uploadFile(11, new File(['x'], 'a.txt'))

      // act
      FakeXhr.last.respond(200, { code: 3003, msg: '채널 멤버가 아닙니다.' })

      // assert
      await expect(promise).rejects.toThrow('채널 멤버가 아닙니다.')
    })

    it('fail case : 413 이면 20MB 안내로, 네트워크 오류면 오류 안내로 실패한다', async () => {
      // arrange
      const tooLarge = uploadFile(11, new File(['x'], 'big.mov'))
      FakeXhr.last.respond(413, '<html>Request Entity Too Large</html>')
      const network = uploadFile(11, new File(['x'], 'a.txt'))

      // act
      FakeXhr.last.onerror()

      // assert
      await expect(tooLarge.promise).rejects.toThrow('20MB 이하만 올릴 수 있어요')
      await expect(network.promise).rejects.toThrow('네트워크 오류로 올리지 못했어요')
    })

    it('success case : 취소하면 AbortError 로 끝난다', async () => {
      // arrange
      const { promise, abort } = uploadFile(11, new File(['x'], 'a.txt'))

      // act
      abort()

      // assert
      await expect(promise).rejects.toMatchObject({ name: 'AbortError' })
    })

    it('success case : 파일 주소는 /v1/private/api/file/{fileId}', () => {
      // arrange & act
      const url = fileUrl('f1')

      // assert
      expect(url).toBe('/v1/private/api/file/f1')
    })
  })
})
