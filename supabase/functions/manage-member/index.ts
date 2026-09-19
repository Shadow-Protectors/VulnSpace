// @ts-nocheck
// manage-member Edge Function
//
// Head-side member moderation. community_members has NO client write policies
// by design — removals go through here with the service key after verifying:
//   * caller is the ACTIVE head of the member's community (or a platform admin)
//   * the target is not themselves a head (heads can't be removed via the app)
// Removal is a soft delete (status -> REMOVED) so content/audit history stays.
//
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "npm:@supabase/supabase-js@2"

console.log("manage-member Edge Function running")

const headers = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers })
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")
    if (!supabaseUrl || !supabaseServiceKey) {
      throw new Error("Missing environment variables for Supabase connection.")
    }
    const supabase = createClient(supabaseUrl, supabaseServiceKey)

    const authHeader = req.headers.get("Authorization")
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing Authorization header" }), { headers, status: 401 })
    }
    const { data: { user }, error: userError } = await supabase.auth.getUser(authHeader.replace("Bearer ", "").trim())
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Invalid or expired session. Please sign in again." }), { headers, status: 401 })
    }

    const { action, community_id, member_id } = await req.json().catch(() => ({}))
    if (!community_id || !member_id) {
      return new Response(JSON.stringify({ error: "Missing community_id or member_id" }), { headers, status: 400 })
    }
    if ((action || "").toUpperCase() !== "REMOVE") {
      return new Response(JSON.stringify({ error: "Unsupported action. Use REMOVE." }), { headers, status: 400 })
    }

    // Caller must be an ACTIVE head of this community...
    const { data: headRows } = await supabase
      .from("community_members")
      .select("id")
      .eq("community_id", community_id)
      .eq("user_id", user.id)
      .in("role", ["COMMUNITY_HEAD", "HEAD"])
      .eq("status", "ACTIVE")
      .limit(1)

    // ...or a platform admin
    let authorized = headRows && headRows.length > 0
    if (!authorized) {
      const { data: adminRows } = await supabase
        .from("platform_admins")
        .select("id")
        .eq("user_id", user.id)
        .limit(1)
      authorized = !!(adminRows && adminRows.length > 0)
    }

    if (!authorized) {
      return new Response(JSON.stringify({ error: "Only the community head can remove members." }), { headers, status: 403 })
    }

    // Load the target member (scoped to this community)
    const { data: members } = await supabase
      .from("community_members")
      .select("id, user_id, username, role, status")
      .eq("id", member_id)
      .eq("community_id", community_id)
      .limit(1)

    const target = members?.[0]
    if (!target) {
      return new Response(JSON.stringify({ error: "Member not found in this community." }), { headers, status: 404 })
    }
    if (target.status === "REMOVED") {
      return new Response(JSON.stringify({ message: "Member already removed.", status: "REMOVED" }), { headers })
    }
    if (target.role === "COMMUNITY_HEAD" || target.role === "HEAD") {
      return new Response(JSON.stringify({ error: "A community head cannot be removed from the app." }), { headers, status: 400 })
    }

    const { error: updateError } = await supabase
      .from("community_members")
      .update({ status: "REMOVED" })
      .eq("id", target.id)

    if (updateError) {
      console.error("member removal failed:", updateError)
      throw new Error("Could not remove the member. Please try again.")
    }

    await supabase.from("audit_logs").insert({
      community_id,
      actor_id: user.id,
      action: "MEMBER_REMOVED",
      target_type: "COMMUNITY_MEMBER",
      target_id: target.id,
      metadata: { removed_user_id: target.user_id, removed_username: target.username },
    }).catch((e: any) => console.warn("audit log non-fatal:", e))

    console.log(`member ${target.id} removed from community ${community_id} by ${user.id}`)

    return new Response(JSON.stringify({ message: "Member removed.", status: "REMOVED" }), { headers })

  } catch (error) {
    console.error("manage-member error:", error)
    return new Response(JSON.stringify({ error: (error as Error).message }), { headers, status: 400 })
  }
})
