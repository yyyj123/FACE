FROM node:24-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
ARG VITE_CHAIN_API=/face-next
ARG VITE_BASE_PATH=/
ENV VITE_CHAIN_API=${VITE_CHAIN_API}
ENV VITE_BASE_PATH=${VITE_BASE_PATH}
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 8080
