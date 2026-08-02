FROM node:22.18-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html vite.config.js ./
COPY src ./src
COPY public ./public
COPY pictures ./pictures
RUN npm run build

FROM nginx:1.28-alpine
COPY deploy/nginx/finals-compass.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80
