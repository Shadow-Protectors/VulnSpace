// @ts-nocheck
// manage-invite-link Edge Function
// Creates or revokes invite codes for a community. Only the ACTIVE head of
// THAT community may call it (checked server-side with the service key).
//
// Fixes vs previous version:
//   - Role check accepted only legacy 'HEAD' -> every real head got 403 after
//     migration 202609191800 renamed roles to COMMUNITY_HEAD. Now accepts both.
//   - Uses the real schema (revoked_at / uses / max_uses) — there is no
//     `status` column on invite_links.
//   - Code generation uses crypto.getRandomValues instead of Math.random.
//
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "npm:@supabase/supabase-js@2"

console.log("manage-invite-link Edge Function running")

const headers = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
}

// CSPRNG invite code, unambiguous alphabet
function generateSecureCode(length = 8): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  const bytes = new Uint8Array(length)
  crypto.getRandomValues(bytes)
  let result = ""
  for (let i = 0; i < length; i++) result += chars[bytes[i] % chars.length]
  return result
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
      return new Response(JSON.stringify({ error: "Invalid or expired session. Please sign out and sign in again." }), { headers, status: 401 })
    }

    const { action, community_id, expires_in_days, max_uses, link_id } = await req.json()

    if (!community_id) throw new Error("Missing community_id")

    // Accept both COMMUNITY_HEAD (current) and HEAD (legacy) roles
    const { data: headRows, error: headError } = await supabase
      .from("community_members")
      .select("id")
      .eq("community_id", community_id)
      .eq("user_id", user.id)
      .in("role", ["COMMUNITY_HEAD", "HEAD"])
      .eq("status", "ACTIVE")
      .limit(1)

    if (headError || !headRows || headRows.length === 0) {
      return new Response(JSON.stringify({ error: "Only the Head of this community can manage invites" }), {
        headers,
        status: 403,
      })
    }

    if (action === "CREATE") {
      const code = generateSecureCode()

      let expires_at = null
      if (expires_in_days && Number(expires_in_days) > 0) {
        const d = new Date()
        d.setDate(d.getDate() + Number(expires_in_days))
        expires_at = d.toISOString()
      }

      const { data: invite, error: createError } = await supabase
        .from("invite_links")
        .insert({
          community_id,
          token_hash: code, // plaintext for shareability in the console; hash in a future hardening pass
          created_by: user.id,
          expires_at,
          max_uses: max_uses && Number(max_uses) > 0 ? Number(max_uses) : null,
        })
        .select()
        .single()

      if (createError) {
        console.error("invite insert failed:", createError)
        throw new Error("Failed to create invite code. Please try again.")
      }

      return new Response(JSON.stringify({ message: "Invite created", code: invite.token_hash, id: invite.id }), { headers })
    } else if (action === "REVOKE") {
      if (!link_id) throw new Error("Missing link_id to revoke")

      const { error: revokeError } = await supabase
        .from("invite_links")
        .update({ revoked_at: new Date().toISOString() })
        .eq("id", link_id)
        .eq("community_id", community_id) // cannot touch another community's links

      if (revokeError) {
        console.error("invite revoke failed:", revokeError)
        throw new Error("Failed to revoke invite code. Please try again.")
      }

      return new Response(JSON.stringify({ message: "Invite revoked successfully" }), { headers })
    } else {
      throw new Error("Invalid action. Use CREATE or REVOKE")
    }

  } catch (error) {
    return new Response(JSON.stringify({ error: (error as Error).message }), {
      headers,
      status: 400,
    })
  }
})
