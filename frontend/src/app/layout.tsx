import type { Metadata } from "next";
import { Schibsted_Grotesk, Source_Serif_4 } from "next/font/google";

import { Providers } from "@/app/providers";
import { SiteHeader } from "@/components/site-header";
import "./globals.css";

const grotesk = Schibsted_Grotesk({ variable: "--font-grotesk", subsets: ["latin"], display: "swap" });
const serif = Source_Serif_4({
  variable: "--font-serif",
  subsets: ["latin"],
  style: ["italic"],
  weight: ["400", "500", "600"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "Meridian — plan trips together",
  description: "Collaborative trip planning: reconcile the group's preferences, then decide together.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${grotesk.variable} ${serif.variable} min-h-dvh`}>
        <Providers>
          <SiteHeader />
          <main className="mx-auto w-full max-w-6xl px-4 pb-24 sm:px-6">{children}</main>
        </Providers>
      </body>
    </html>
  );
}
