FROM node:24-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
ARG VITE_PORTAL_MODE=customer
ARG VITE_BASE_PATH=/
ARG VITE_CLIENT_API_BASE=/face-next/api/v2/client
ARG VITE_V3_API_BASE=/face-next/api/v3
ENV VITE_PORTAL_MODE=${VITE_PORTAL_MODE}
ENV VITE_BASE_PATH=${VITE_BASE_PATH}
ENV VITE_CLIENT_API_BASE=${VITE_CLIENT_API_BASE}
ENV VITE_V3_API_BASE=${VITE_V3_API_BASE}
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 8080
