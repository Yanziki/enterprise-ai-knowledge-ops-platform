FROM node:26.5.1-alpine3.23 AS build
RUN npm install --global pnpm@11.9.0
WORKDIR /workspace

COPY apps/web/package.json apps/web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY apps/web .
RUN pnpm build

FROM nginxinc/nginx-unprivileged:1.29.0-alpine-slim AS runtime
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY --chown=101:101 infra/nginx/default.conf /etc/nginx/conf.d/default.conf

USER 101
EXPOSE 8080
