FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
ARG VITE_API_BASE
ENV VITE_API_BASE=${VITE_API_BASE}
RUN npm run build

FROM nginx:1.29-alpine
COPY infra/nginx/frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
COPY THIRD-PARTY-NOTICES.md /usr/share/nginx/html/legal/THIRD-PARTY-NOTICES.md
COPY THIRD-PARTY-FRONTEND.txt /usr/share/nginx/html/legal/THIRD-PARTY-FRONTEND.txt
COPY LICENSES /usr/share/nginx/html/legal/LICENSES
EXPOSE 80
