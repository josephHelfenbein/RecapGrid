// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  ssr: false,
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_URL || 'http://localhost:8080/api'
    }
  },
  compatibilityDate: '2024-11-01',
  devtools: { enabled: true }
})