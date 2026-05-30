FROM python:3.12-slim

ARG OHA_VERSION=1.4.7
ARG TARGETARCH
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL -o /usr/local/bin/oha \
       "https://github.com/hatoo/oha/releases/download/v${OHA_VERSION}/oha-linux-${TARGETARCH}" \
    && chmod +x /usr/local/bin/oha \
    && apt-get purge -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY parse.py run.py ./

CMD ["python3", "run.py"]
