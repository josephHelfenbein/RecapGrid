FROM node:18 AS build

WORKDIR /app

COPY package.json pnpm.lock ./
RUN pnpm install --frozen-lockfile

COPY . .

RUN pnpm build

FROM node:18

WORKDIR /app

COPY --from=build /app/.output .output

RUN pnpm add nuxt@latest

EXPOSE 3000

CMD ["pnpm", "nuxt", "start"]
