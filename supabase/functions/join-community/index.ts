// @ts-nocheck
// join-community Edge Function
//
// The ONLY path that creates community_members rows for regular members
// (the table has no client INSERT policy — by design). It enforces everything
// the old client-side flow could not:
//   * invite code exists, is not revoked, is not expired, has uses left
//   * usage counter is incremented atomically (consume_invite, race-safe)
//   * rejoining with the same account is idempotent (no duplicate rows)
//   * per-community username uniqueness produces a friendly 409
//
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "npm:@supabase/supabase-js@2"

console.log("join-community Edge Function running")

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

    // 1. Authenticate the caller (anonymous sessions are fine — they have a JWT)
    const authHeader = req.headers.get("Authorization")
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing Authorization header" }), { headers, status: 401 })
    }

    const { data: { user }, error: userError } = await supabase.auth.getUser(authHeader.replace("Bearer ", "").trim())
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Invalid or expired session. Please try again." }), { headers, status: 401 })
    }

    // 2. Parse + validate payload
    const { code, username } = await req.json().catch(() => ({}))
    const trimmedCode = (code || "").toString().trim().toUpperCase()
    const trimmedUsername = (username || "").toString().trim()

    if (!trimmedCode) {
      return new Response(JSON.stringify({ error: "Invite code is required." }), { headers, status: 400 })
    }
    if (!trimmedUsername || trimmedUsername.length < 2 || trimmedUsername.length > 30) {
      return new Response(JSON.stringify({ error: "Username must be 2–30 characters." }), { headers, status: 400 })
    }

    // 3. Look up the invite
    const { data: invites, error: inviteError } = await supabase
      .from("invite_links")
      .select("id, community_id, revoked_at, expires_at, max_uses, uses")
      .eq("token_hash", trimmedCode)
      .limit(1)

    if (inviteError) {
      console.error("invite lookup failed:", inviteError)
      throw new Error("Could not validate the invite code. Please try again.")
    }

    const invite = invites?.[0]
    const now = new Date()
    const isUsable = !!invite
      && !invite.revoked_at
      && (!invite.expires_at || new Date(invite.expires_at) > now)
      && (invite.max_uses == null || invite.uses < invite.max_uses)

    if (!isUsable) {
      return new Response(JSON.stringify({ error: "Invalid, expired or exhausted invite code." }), { headers, status: 404 })
    }

    // 4. Idempotent: already a member? Just confirm.
    const { data: existing } = await supabase
      .from("community_members")
      .select("id")
      .eq("community_id", invite.community_id)
      .eq("user_id", user.id)
      .limit(1)

    if (existing && existing.length > 0) {
      return new Response(JSON.stringify({ message: "You are already a member of this community.", community_id: invite.community_id, already_member: true }), { headers })
    }

    // 5. Create the membership
    const { error: insertError } = await supabase
      .from("community_members")
      .insert({
        community_id: invite.community_id,
        user_id: user.id,
        username: trimmedUsername,
        role: "MEMBER",
        status: "ACTIVE",
      })

    if (insertError) {
      console.error("member insert failed:", insertError)
      if (insertError.message?.includes("duplicate") || insertError.code === "23505") {
        return new Response(JSON.stringify({ error: "That username is already taken in this community. Pick another one." }), { headers, status: 409 })
      }
      throw new Error("Could not join the community. Please try again.")
    }

    // 6. Consume one use — atomic + double-checks validity server-side.
    //    Non-fatal if it reports false (invite was exhausted by a concurrent join);
    //    the member row already exists, so report success.
    const { data: consumed, error: consumeError } = await supabase.rpc("consume_invite", { p_invite_id: invite.id })
    if (consumeError) {
      console.warn("consume_invite rpc failed (non-fatal):", consumeError)
    } else if (consumed === false) {
      console.warn(`invite ${invite.id} reached its limit concurrently`)
    }

    console.log(`user ${user.id} joined community ${invite.community_id} as ${trimmedUsername}`)

    return new Response(JSON.stringify({
      message: "Successfully joined community",
      community_id: invite.community_id,
      username: trimmedUsername,
    }), { headers })

  } catch (error) {
    console.error("join-community error:", error)
    return new Response(JSON.stringify({ error: (error as Error).message }), {
      headers,
      status: 400,
    })
  }
})
