FROM node:22-alpine AS builder

WORKDIR /workspace
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /workspace/dist /usr/share/nginx/html
EXPOSE 80
