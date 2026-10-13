import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// 시간 / 날짜 표기 테스트가 어느 컴퓨터에서든 같게 나오도록 한국 시간으로 고정
process.env.TZ = 'Asia/Seoul'

afterEach(cleanup)
