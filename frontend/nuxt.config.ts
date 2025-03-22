// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  routeRules: {
    '/': { prerender: false },
    '/api/**': { proxy: process.env.NUXT_PUBLIC_API_URL + '/**' },
  },
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_URL,
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
