FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

ARG NODE_VERSION=22.23.2
USER root

RUN set -eux; \
    rm -f /etc/apt/sources.list.d/yarn.list /etc/apt/sources.list.d/yarn.sources; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        maven \
        ripgrep \
        jq \
        netcat-openbsd; \
    rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
      -o /tmp/node.tar.xz; \
    tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1; \
    rm -f /tmp/node.tar.xz; \
    ln -sf /usr/local/bin/node /usr/bin/node; \
    npm install -g npm@latest; \
    node -v; npm -v; mvn -v

USER vscode
