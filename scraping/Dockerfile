FROM node:20-slim

# Instalar dependencias del sistema requeridas por Chromium / Puppeteer
RUN apt-get update && apt-get install -y --no-install-recommends \
    libnss3 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    libgbm1 \
    libasound2 \
    libpango-1.0-0 \
    libxshmfence1 \
    fonts-liberation \
    ca-certificates \
    libxkbcommon0 \
    libxfixes3 \
    libgtk-3-0 \
    libx11-6 \
    libx11-xcb1 \
    libxcb1 \
    libxcursor1 \
    libxi6 \
    libxrender1 \
    libxss1 \
    libxtst6 \
    && rm -rf /var/lib/apt/lists/*

ENV PUPPETEER_CACHE_DIR=/home/node/.cache/puppeteer

WORKDIR /app

RUN mkdir -p /home/node/.cache/puppeteer && chown -R node:node /home/node

# Copiar manifiestos de dependencias
COPY package.json package-lock.json ./

# Instalación de paquetes y browser, asegurando permisos para el usuario 'node'
RUN npm ci --omit=dev && \
    npx puppeteer browsers install chrome && \
    chown -R node:node /home/node/.cache

# Copiar código fuente
COPY src/ ./src/

ENV NODE_ENV=production
ENV PORT=3000

# Usuario sin privilegios provisto por node:20-slim
USER node

EXPOSE 3000

CMD ["node", "src/index.js"]