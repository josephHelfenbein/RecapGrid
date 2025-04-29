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
  },
  app: {
    head: {
      titleTemplate: 'RecapGrid - %s',
      meta: [
        {
          name: 'description',
          content: 'Instantly turn videos into shareable highlight reels with smart timestamps, AI narration, and background music.',
        },
        {
          name: 'keywords',
          content: 'Video highlights, Video summarization, AI video summary, Automated timestamping, Shareable highlight reels, AI narration, Background music video, Video clip editor, Quick video recap, Social media videos, Content repurposing, Video editing automation, Timestamp extractor, Smart video trimming, Highlight generator',
        },
        {
          property: 'og:title',
          content: 'RecapGrid - Instantly Summarize & Share Your Best Video Moments',
        },
        {
          property: 'og:description',
          content: 'Instantly turn videos into shareable highlight reels with smart timestamps, AI narration, and background music.',
        },
        {
          property: 'og:url',
          content: 'https://www.recapgrid.com',
        },
        {
          name: 'twitter:title',
          content: 'PairGrid - Instantly Summarize & Share Your Best Video Moments',
        },
        {
          name: 'twitter:description',
          content: 'Instantly turn videos into shareable highlight reels with smart timestamps, AI narration, and background music.',
        },
      ],
      link: [
        {
          rel: 'icon',
          type: 'image/png',
          href: '/favicon-96x96.png',
          sizes: '96x96',
        },
        {
          rel: 'icon',
          type: 'image/svg+xml',
          href: '/favicon.svg',
        },
        {
          rel: 'shortcut icon',
          href: '/favicon.ico',
        },
        {
          rel: 'apple-touch-icon',
          sizes: '180x180',
          href: '/apple-touch-icon.png',
        },
        {
          rel: 'manifest',
          href: '/site.webmanifest',
        },
      ],
    },
  },
});
