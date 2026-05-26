FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:1.25-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /tmp/nginx.conf.template
EXPOSE 80
ENTRYPOINT []
CMD ["/bin/sh", "-c", "envsubst '${PORT}' < /tmp/nginx.conf.template > /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'"]
