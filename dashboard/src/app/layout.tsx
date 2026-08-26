import type { Metadata } from "next";
import { DM_Mono, DM_Sans } from "next/font/google";
import Shell from "@/components/Shell";
import "./globals.css";

const dmSans = DM_Sans({
  variable: "--font-dm-sans",
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700"],
  style: ["normal", "italic"],
});

const dmMono = DM_Mono({
  variable: "--font-dm-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});

export const metadata: Metadata = {
  title: "Jalraksha One",
  description: "Live water-quality monitoring — temperature and turbidity",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={`${dmSans.variable} ${dmMono.variable}`}>
        <Shell>{children}</Shell>
      </body>
    </html>
  );
}
