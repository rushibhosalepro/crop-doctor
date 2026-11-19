import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Run with: npm run dev   (serves http://localhost:3000)
// Build with: npm run build (outputs dist/, serve it for the demo)
export default defineConfig({
  plugins: [react()],
  server: { port: 3000 },
});
