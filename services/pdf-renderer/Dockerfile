FROM node:22-bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends chromium fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /renderer
COPY services/pdf-renderer/server.mjs ./server.mjs

ENV PDF_RENDERER_HOST=0.0.0.0 \
    PDF_RENDERER_PORT=8787 \
    CHROME_PATH=/usr/bin/chromium

USER node
EXPOSE 8787
CMD ["node", "server.mjs"]
