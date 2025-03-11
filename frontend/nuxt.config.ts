// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  routeRules: {
    '/': { prerender: false },
  },
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_URL
    }
  },
  compatibilityDate: '2024-11-01',
  devtools: { enabled: true },
  modules: ['@nuxtjs/tailwindcss', 'shadcn-nuxt', '@clerk/nuxt'],
  nitro: {
    devProxy: {
      '/api/': {
        target: process.env.NUXT_PUBLIC_API_URL,
        changeOrigin: true,
        prependPath: false,
      }
    }
  },
  shadcn: {
    prefix: '',
    componentDir: './components/ui'
  }
});
