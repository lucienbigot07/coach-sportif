FROM python:3.13-slim
WORKDIR /app
RUN useradd --create-home --uid 10001 coach && mkdir /data && chown coach /data
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY coach_mcp ./coach_mcp
USER coach
ENV COACH_STATE_DB=/data/coach-mcp.sqlite3 PORT=8080
EXPOSE 8080
# --proxy-headers : l'hébergeur termine TLS devant le conteneur
CMD ["sh", "-c", "uvicorn coach_mcp.server:app --host 0.0.0.0 --port ${PORT} --proxy-headers --forwarded-allow-ips='*'"]
