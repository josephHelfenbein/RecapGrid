FROM node:18 AS frontend-build

WORKDIR /app

RUN npm install -g pnpm@8.15.4

COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY frontend/ ./
RUN pnpm build

FROM nginx:alpine

WORKDIR /usr/share/nginx/html

COPY --from=frontend-build /app/.output/public /usr/share/nginx/html

COPY frontend/nginx.conf /etc/nginx/nginx.conf
COPY frontend/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]
