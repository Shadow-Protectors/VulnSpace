// @ts-nocheck
// @ts-ignore (Suppresses IDE warning for Deno URL imports)
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "npm:@supabase/supabase-js@2"

console.log("Hello from manage-invite-link!")

// Helper to generate a random code
function generateSecureCode(length = 8) {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let result = ''
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

serve(async (req: Request) => {
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")

    if (!supabaseUrl || !supabaseServiceKey) {
      throw new Error("Missing environment variables for Supabase connection.")
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey)

    // Extract auth header to identify the caller
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      throw new Error("Missing Authorization header")
    }

    // Get the user from the auth token
    const { data: { user }, error: userError } = await supabase.auth.getUser(authHeader.replace('Bearer ', ''))
    if (userError || !user) {
      throw new Error("Invalid token")
    }

    // Parse request body
    const { action, community_id, expires_in_days, max_uses, link_id } = await req.json()

    if (!community_id) throw new Error("Missing community_id")

    // Verify caller is a HEAD of THIS specific community
    const { data: isHead, error: headError } = await supabase
      .from('community_members')
      .select('id')
      .eq('community_id', community_id)
      .eq('user_id', user.id)
      .eq('role', 'HEAD')
      .eq('status', 'ACTIVE')
      .single()

    if (headError || !isHead) {
      return new Response(JSON.stringify({ error: "Unauthorized: Only the Head of this community can manage invites" }), {
        headers: { "Content-Type": "application/json" },
        status: 403,
      })
    }

    if (action === 'CREATE') {
      const code = generateSecureCode()
      
      let expires_at = null
      if (expires_in_days) {
        const d = new Date()
        d.setDate(d.getDate() + expires_in_days)
        expires_at = d.toISOString()
      }

      const { data: invite, error: createError } = await supabase
        .from('invite_links')
        .insert({
          community_id,
          token_hash: code, // In a highly secure app we would hash this, but raw makes UI easier for now
          created_by: user.id,
          expires_at,
          max_uses: max_uses || null
        })
        .select()
        .single()

      if (createError) throw new Error("Failed to create invite link")

      return new Response(JSON.stringify({ message: "Invite created", code: invite.token_hash }), {
        headers: { "Content-Type": "application/json" },
      })
    } else if (action === 'REVOKE') {
      if (!link_id) throw new Error("Missing link_id to revoke")

      await supabase
        .from('invite_links')
        .update({ revoked_at: new Date().toISOString() })
        .eq('id', link_id)
        .eq('community_id', community_id)

      return new Response(JSON.stringify({ message: "Invite revoked successfully" }), {
        headers: { "Content-Type": "application/json" },
      })
    } else {
      throw new Error("Invalid action. Use CREATE or REVOKE")
    }

  } catch (error) {
    return new Response(JSON.stringify({ error: (error as Error).message }), {
      headers: { "Content-Type": "application/json" },
      status: 400,
    })
  }
})
