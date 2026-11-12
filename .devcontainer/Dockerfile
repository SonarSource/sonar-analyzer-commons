FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends bash ca-certificates curl git unzip xz-utils \
    && rm -rf /var/lib/apt/lists/*
RUN curl --fail --silent --show-error --location https://mise.run \
    | MISE_VERSION=2026.10.20 MISE_INSTALL_PATH=/usr/local/bin/mise sh
