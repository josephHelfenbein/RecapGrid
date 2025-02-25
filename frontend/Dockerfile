FROM node:18 AS frontend-build

WORKDIR /app

RUN npm install -g pnpm@8.15.4

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY / ./
RUN pnpm build

FROM nginx:alpine

WORKDIR /usr/share/nginx/html

COPY --from=frontend-build /app/.output /usr/share/nginx/html

COPY nginx.conf /etc/nginx/nginx.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]
