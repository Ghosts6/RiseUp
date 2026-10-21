FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
# Vite inlines these at build time; the browser calls the API directly.
ARG VITE_API_URL=http://localhost:8000
ARG VITE_API_TOKEN=dev-token
ENV VITE_API_URL=$VITE_API_URL VITE_API_TOKEN=$VITE_API_TOKEN
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
