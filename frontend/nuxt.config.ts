// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  routeRules: {
    '/': { prerender: false },
    "/api/**": { proxy: "https://recapgrid-backend-378320393490.us-central1.run.app/api/**" },
  },
  runtimeConfig: {
    public: {
      apiBase: "https://recapgrid-backend-378320393490.us-central1.run.app",
    },
  },
  compatibilityDate: '2024-11-01',
  devtools: { enabled: true },
  modules: ['@nuxtjs/tailwindcss', 'shadcn-nuxt', '@clerk/nuxt'],
  shadcn: {
    prefix: '',
    componentDir: './components/ui',
  }
});
