import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Keep frontend development on a fixed local origin and proxy all business APIs to Java chengwenfang-app.
export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8001",
        changeOrigin: false
      }
    }
  }
});
