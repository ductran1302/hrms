FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
ARG VITE_API_BASE
ENV VITE_API_BASE=${VITE_API_BASE}
RUN npm run build

FROM nginx:1.27-alpine
COPY infra/nginx/frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
