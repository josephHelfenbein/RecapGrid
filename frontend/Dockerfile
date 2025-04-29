FROM node:23-slim AS frontend-build

WORKDIR /app

RUN npm install -g pnpm@10.9.0
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . ./
RUN pnpm build

FROM node:23-slim

WORKDIR /app

RUN npm install -g pnpm@10.9.0

COPY --from=frontend-build /app /app

EXPOSE 8080

CMD ["pnpm", "start"]
