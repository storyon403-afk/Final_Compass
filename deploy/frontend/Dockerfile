FROM node:22.18-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/package.json
COPY apps/livedoc/package.json apps/livedoc/package.json
RUN npm ci
COPY apps/web ./apps/web
COPY apps/livedoc ./apps/livedoc
RUN npm run build

FROM nginx:1.28-alpine
COPY deploy/nginx/finals-compass.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80
