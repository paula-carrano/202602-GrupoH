FROM node:20-slim

# Instalar dependencias del sistema requeridas por Chromium / Puppeteer
# (Nota: no se instala curl ni wget; el healthcheck utiliza el runtime de Node.js)
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
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copiar manifiestos de dependencias
COPY package.json package-lock.json ./

# Instalación determinística estricta para producción
RUN npm ci --omit=dev

# Copiar código fuente
COPY src/ ./src/

ENV NODE_ENV=production
ENV PORT=3000

# Usuario sin privilegios provisto por node:20-slim
USER node

EXPOSE 3000

CMD ["node", "src/index.js"]
