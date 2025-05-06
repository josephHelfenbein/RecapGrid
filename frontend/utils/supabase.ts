import { createClient } from '@supabase/supabase-js';
export default defineNuxtPlugin((nuxtApp) => {
    const config = useRuntimeConfig();
    const supabaseUrl = config.public.supabaseUrl!.toString();
    const supabaseKey = config.public.supabaseKey!.toString();
  
    const $supabase = createClient(supabaseUrl, supabaseKey);
    nuxtApp.provide('$supabase', $supabase)
  });