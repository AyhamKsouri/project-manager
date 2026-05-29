FROM node:18-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm install --legacy-peer-deps --no-package-lock

FROM deps AS dev
WORKDIR /app
ENV CHOKIDAR_USEPOLLING=true
ENV WATCHPACK_POLLING=true
COPY . .
EXPOSE 4200
USER node
CMD ["npm", "run", "start:docker"]

FROM deps AS build
WORKDIR /app
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine AS runtime
COPY --from=build /app/dist/project-manager-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
