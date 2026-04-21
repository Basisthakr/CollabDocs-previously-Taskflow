# ============================================================
# Frontend Dockerfile — multi-stage build
#
# IMPORTANT: Vite bakes environment variables into the JS
# bundle at BUILD TIME, not at runtime.  That means
# VITE_API_URL and VITE_WS_URL must be supplied as Docker
# build-args (--build-arg), NOT as container env vars.
#
# docker-compose.yml passes them automatically via the
# `args:` section under `build:`.
# ============================================================

# ---- Stage 1: build ----
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies first for layer caching.
COPY package.json package-lock.json* ./
RUN npm ci --prefer-offline

# Receive build-time configuration.
ARG VITE_API_URL=http://localhost:8080
ARG VITE_WS_URL=ws://localhost:8080/docs/ws

# Vite reads VITE_* from the process environment during build.
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_WS_URL=$VITE_WS_URL

COPY . .
RUN npm run build

# ---- Stage 2: serve ----
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
