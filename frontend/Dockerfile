# Multi-stage: production Angular build, served by unprivileged nginx.

FROM node:22-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration=production

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
