import assert from 'node:assert/strict'
import test from 'node:test'
import { parseSse } from './sse.js'

test('parses SSE frames split across chunks', async () => {
  const encoder = new TextEncoder()
  const response = new Response(new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode('event: delta\ndata: {"te'))
      controller.enqueue(encoder.encode('xt":"你好"}\n\nevent: done\ndata: {"traceId":"t1"}\n\n'))
      controller.close()
    },
  }))
  const events = []
  for await (const event of parseSse(response)) events.push(event)
  assert.deepEqual(events, [
    { event: 'delta', data: { text: '你好' } },
    { event: 'done', data: { traceId: 't1' } },
  ])
})
