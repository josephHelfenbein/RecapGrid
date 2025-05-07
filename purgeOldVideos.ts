import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
Deno.serve(async ()=>{
  console.log("purgeOldVideos invoked at", new Date().toISOString());
  const supabase = createClient(Deno.env.get("SUPABASE_URL"), Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"));
  const cutoff = new Date(Date.now() - 8 * 60 * 60 * 1000).toISOString();
  async function purgeTableBucket(table, bucket, rowKey = "id", userCol = "user_id", fileCol = "file_name") {
    console.log(`→ Purging ${table} (uploaded_at < ${cutoff})`);
    const { data: rows, error: fetchErr } = await supabase.from(table).select(`${rowKey}, ${userCol}, ${fileCol}`).lt("uploaded_at", cutoff);
    if (fetchErr) {
      console.error(`Error fetching from ${table}:`, fetchErr);
      return;
    }
    if (!rows || rows.length === 0) {
      console.log(`No expired rows in ${table}`);
      return;
    }
    for (const row of rows){
      const userId = row[userCol];
      const fileName = row[fileCol];
      const path = `${userId}/${fileName}`;
      const { error: delErr } = await supabase.storage.from(bucket).remove([
        path
      ]);
      if (delErr) {
        console.error(`Failed to delete storage ${bucket}/${path}:`, delErr);
        continue;
      }
      const { error: dbErr } = await supabase.from(table).delete().eq(rowKey, row[rowKey]);
      if (dbErr) {
        console.error(`Failed to delete row id=${row[rowKey]} from ${table}:`, dbErr);
      } else {
        console.log(`Purged ${table} id=${row[rowKey]} & file ${bucket}/${path}`);
      }
    }
  }
  try {
    await purgeTableBucket("videos", "videos");
    await purgeTableBucket("processed", "processed");
    return new Response("Purge complete", {
      status: 200
    });
  } catch (err) {
    console.error("Unexpected error in purgeOldVideos:", err);
    return new Response("Unexpected error", {
      status: 500
    });
  }
});
