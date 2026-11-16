# ── Build ────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
# Exact versions from package-lock.json; the cache mount keeps npm's download cache between builds.
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# Same-origin through the nginx container, so the API is just /api — no CORS, and the
# auth cookie is first-party. Override only if the API lives on another host.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ── Serve ────────────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
