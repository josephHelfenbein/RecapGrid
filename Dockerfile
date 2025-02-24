FROM node:18 AS frontend-build

WORKDIR /app

RUN npm install -g pnpm@8.15.4

COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY frontend/ ./
RUN pnpm build

FROM eclipse-temurin:17-jdk AS backend-build

WORKDIR /app

COPY backend/target/*.jar app.jar

FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY backend/target/*.jar /app/app.jar

COPY --from=frontend-build /app/.output /app/.output

RUN apt-get update && apt-get install -y nginx

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 8080 3000

CMD ["sh", "-c", "java -Dserver.port=8080 -jar /app/app.jar & nginx -g 'daemon off;'"]
