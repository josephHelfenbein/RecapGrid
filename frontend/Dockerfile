FROM node:18 AS frontend-build

WORKDIR /app

RUN npm install -g pnpm@8.15.4

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY / ./
RUN pnpm build

FROM node:18 AS frontend-ssr

WORKDIR /app

COPY --from=frontend-build /app/.output /app/.output

RUN npm install -g serve

EXPOSE 8080

COPY nginx.conf /etc/nginx/nginx.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV API_URL=http://your-backend-service:8080

ENTRYPOINT ["/entrypoint.sh"]
