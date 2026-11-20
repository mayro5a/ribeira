import type { Metadata, Viewport } from "next";
import { AppProvider } from "@/features/app/AppProvider";
import { ServiceWorkerManager } from "@/features/app/ServiceWorkerManager";
import "./globals.css";

export const metadata: Metadata = {
  title: "Ribeira",
  description: "Gestão de doações de alimentos para comunidades ribeirinhas",
  manifest: "/manifest.webmanifest",
  icons: { icon: "/icons/icon.svg", apple: "/icons/icon-192.png" },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#0b5661",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <body>
        <AppProvider>
          <ServiceWorkerManager />
          {children}
        </AppProvider>
      </body>
    </html>
  );
}
