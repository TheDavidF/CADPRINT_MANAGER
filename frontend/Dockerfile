FROM node:22-bookworm-slim AS source
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

FROM source AS manager-build
RUN npm run build

FROM source AS storefront-build
RUN npx --no-install vite build --config vite.storefront.config.ts

FROM nginx:1.28-alpine AS storefront
COPY nginx-storefront.conf /etc/nginx/conf.d/default.conf
COPY --from=storefront-build /src/dist-storefront /usr/share/nginx/html

FROM nginx:1.28-alpine AS manager
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=manager-build /src/dist /usr/share/nginx/html
