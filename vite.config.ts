import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Keep frontend development on a fixed local origin and proxy all business APIs to Java chengwenfang-app.
// Override with: set VITE_DEV_PORT=5174 && set VITE_API_PROXY=http://127.0.0.1:8002
const devPort = Number(process.env.VITE_DEV_PORT || 5173);
const apiProxy = process.env.VITE_API_PROXY || "http://127.0.0.1:8001";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: devPort,
    strictPort: true,
    proxy: {
      "/api": {
        target: apiProxy,
        changeOrigin: false
      }
    }
  }
});
