import type { NextConfig } from "next";

// Export estático: o app inteiro vira arquivos servidos e pré-armazenados pelo
// Service Worker, permitindo abrir qualquer tela sem conexão (ADR-0006).
const nextConfig: NextConfig = {
  output: "export",
  trailingSlash: true,
  images: { unoptimized: true },
  reactStrictMode: true,
  poweredByHeader: false,
};

export default nextConfig;
