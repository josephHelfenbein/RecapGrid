FROM node:18 AS frontend-build

WORKDIR /app

RUN npm install -g pnpm@8.15.4
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . ./
RUN pnpm build

FROM node:18

WORKDIR /app

RUN npm install -g pnpm@8.15.4

COPY --from=frontend-build /app /app

EXPOSE 8080

CMD ["pnpm", "run", "start"]
