export async function* parseSse(response) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""
  for (;;) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    let index
    while ((index = buffer.indexOf("\n\n")) >= 0) {
      const frame = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      let event = "message"
      let data = ""
      for (const line of frame.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim()
        else if (line.startsWith("data:")) data += line.slice(5).trim()
      }
      if (data) yield { event, data: JSON.parse(data) }
    }
    if (done) break
  }
}
