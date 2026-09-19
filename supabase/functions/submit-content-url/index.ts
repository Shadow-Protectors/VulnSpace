// @ts-nocheck
// submit-content-url Edge Function
//
// Turns a member-submitted link into a published feed card:
//   1. Verify the caller's JWT and ACTIVE membership in the community
//   2. Validate the URL (http/https only)
//   3. Reject duplicates already live in the community (409)
//   4. Create content (PROCESSING) + processing_jobs rows for audit
//   5. Fetch the page server-side, extract title/description/site name
//   6. Apply a lightweight safety verdict (scheme + blocklist heuristics)
//   7. Publish (or PROCESSING_FAILED / REMOVED+BLOCKED) and return the card
//
// All DB writes run as service_role — the client writes to `content` directly
// only through its RLS-scoped update paths (bookmarks etc.), never here.
//
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "npm:@supabase/supabase-js@2"

console.log("submit-content-url Edge Function running")

const headers = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
}

const EVENT_CATEGORIES = new Set(["CTF", "HACKATHON", "INTERNSHIP", "CONFERENCE", "WORKSHOP"])
const ALL_CATEGORIES = new Set([
  ...EVENT_CATEGORIES,
  "STUDY_MATERIAL", "TOOL", "WRITEUP", "COURSE", "DOCUMENTATION", "OTHER_RESOURCE",
])

// Crude first-line blocklist — real deployments should plug in a threat feed.
const BLOCKED_DOMAIN_PARTS = ["grabify", "iplogger", "blasze", "yip.su", "phishing"]

function pickMeta(html: string, patterns: RegExp[]): string | null {
  for (const re of patterns) {
    const m = html.match(re)
    if (m?.[1]) {
      const cleaned = decodeEntities(m[1].trim())
      if (cleaned) return cleaned
    }
  }
  return null
}

function decodeEntities(s: string): string {
  return s
    .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/\s+/g, " ")
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

    // ---- 1. Auth -------------------------------------------------------------
    const authHeader = req.headers.get("Authorization")
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing Authorization header" }), { headers, status: 401 })
    }
    const { data: { user }, error: userError } = await supabase.auth.getUser(authHeader.replace("Bearer ", "").trim())
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Invalid or expired session. Please sign in again." }), { headers, status: 401 })
    }

    // ---- 2. Payload ----------------------------------------------------------
    const body = await req.json().catch(() => ({}))
    const communityId = (body.community_id || body.communityId || "").toString()
    const rawUrl = (body.url || "").toString().trim()
    const category = ALL_CATEGORIES.has(String(body.category || "").toUpperCase())
      ? String(body.category).toUpperCase()
      : "OTHER_RESOURCE"
    const contentType = EVENT_CATEGORIES.has(category) ? "EVENT" : "RESOURCE"

    if (!communityId) {
      return new Response(JSON.stringify({ error: "Missing community." }), { headers, status: 400 })
    }

    let parsed: URL
    try {
      parsed = new URL(rawUrl)
      if (parsed.protocol !== "http:" && parsed.protocol !== "https:") throw new Error("bad scheme")
    } catch {
      return new Response(JSON.stringify({ error: "Enter a valid URL starting with https://", status: "FAILED" }), { headers, status: 400 })
    }
    const sourceDomain = parsed.hostname.replace(/^www\./, "")

    // ---- 3. Membership -------------------------------------------------------
    const { data: memberRows } = await supabase
      .from("community_members")
      .select("id, username")
      .eq("community_id", communityId)
      .eq("user_id", user.id)
      .eq("status", "ACTIVE")
      .limit(1)

    if (!memberRows || memberRows.length === 0) {
      return new Response(JSON.stringify({ error: "Only active members can submit links to this community." }), { headers, status: 403 })
    }
    const submitterUsername = memberRows[0].username

    // ---- 4. Duplicate guard ----------------------------------------------------
    const { data: dupes } = await supabase
      .from("content")
      .select("id")
      .eq("community_id", communityId)
      .eq("source_url", rawUrl)
      .neq("status", "REMOVED")
      .limit(1)

    if (dupes && dupes.length > 0) {
      return new Response(JSON.stringify({
        error: "This link was already submitted to your community.",
        status: "DUPLICATE",
        content_id: dupes[0].id,
      }), { headers, status: 409 })
    }

    // ---- 5. Create PROCESSING rows ---------------------------------------------
    const { data: contentRow, error: insertError } = await supabase
      .from("content")
      .insert({
        community_id: communityId,
        submitted_by: user.id,
        title: sourceDomain,
        source_url: rawUrl,
        source_domain: sourceDomain,
        content_type: contentType,
        category,
        status: "PROCESSING",
        safety_status: "UNKNOWN",
        priority: contentType === "EVENT" ? 50 : 100,
      })
      .select("id")
      .single()

    if (insertError || !contentRow) {
      console.error("content insert failed:", insertError)
      throw new Error("Could not save the submission. Please try again.")
    }
    const contentId = contentRow.id

    const { data: jobRow } = await supabase
      .from("processing_jobs")
      .insert({
        content_id: contentId,
        community_id: communityId,
        submitted_by: user.id,
        source_url: rawUrl,
        state: "RUNNING",
        started_at: new Date().toISOString(),
        attempts: 1,
      })
      .select("id")
      .single()

    // ---- 6. Fetch + extract ----------------------------------------------------
    let pageHtml: string | null = null
    try {
      const pageRes = await fetch(rawUrl, {
        redirect: "follow",
        signal: AbortSignal.timeout(9000),
        headers: {
          "User-Agent": "VulnSpaceBot/1.0 (+link preview fetcher)",
          "Accept": "text/html,application/xhtml+xml",
        },
      })
      if (pageRes.ok) {
        const raw = await pageRes.text()
        pageHtml = raw.slice(0, 512_000) // cap work on huge pages
      }
    } catch (fetchErr) {
      console.warn("page fetch failed:", fetchErr)
    }

    if (pageHtml === null) {
      await supabase.from("content").update({
        status: "PROCESSING_FAILED",
        safety_status: "UNKNOWN",
        last_checked_at: new Date().toISOString(),
      }).eq("id", contentId)
      if (jobRow) {
        await supabase.from("processing_jobs").update({
          state: "FAILED", finished_at: new Date().toISOString(), error_message: "Page fetch failed",
        }).eq("id", jobRow.id)
      }
      return new Response(JSON.stringify({
        error: "We couldn't read that link. Check that it opens publicly, then try again.",
        status: "FAILED",
      }), { headers, status: 502 })
    }

    const title = pickMeta(pageHtml, [
      /<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']/i,
      /<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:title["']/i,
      /<meta[^>]+name=["']twitter:title["'][^>]+content=["']([^"']+)["']/i,
      /<title[^>]*>([^<]+)<\/title>/i,
    ]) ?? sourceDomain

    const description = pickMeta(pageHtml, [
      /<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']+)["']/i,
      /<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:description["']/i,
      /<meta[^>]+name=["']description["'][^>]+content=["']([^"']+)["']/i,
    ])

    const organizer = pickMeta(pageHtml, [
      /<meta[^>]+property=["']og:site_name["'][^>]+content=["']([^"']+)["']/i,
      /<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:site_name["']/i,
    ])

    // ---- 7. Safety verdict -------------------------------------------------------
    const haystack = `${sourceDomain}${parsed.pathname}`.toLowerCase()
    const isBlocked = BLOCKED_DOMAIN_PARTS.some((bad) => haystack.includes(bad))
    const safetyStatus = isBlocked
      ? "BLOCKED"
      : (parsed.protocol === "http:" ? "NEEDS_REVIEW" : "LOW_RISK")
    const newStatus = isBlocked ? "REMOVED" : "PUBLISHED"

    await supabase.from("content").update({
      title: title.slice(0, 200),
      description: description?.slice(0, 500) ?? null,
      organizer: organizer?.slice(0, 120) ?? null,
      final_url: rawUrl,
      status: newStatus,
      safety_status: safetyStatus,
      safety_reason: isBlocked ? "Domain matched a blocklist pattern" : null,
      last_checked_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }).eq("id", contentId)

    if (jobRow) {
      await supabase.from("processing_jobs").update({
        state: "SUCCEEDED", finished_at: new Date().toISOString(),
      }).eq("id", jobRow.id)
    }

    await supabase.from("audit_logs").insert({
      community_id: communityId,
      actor_id: user.id,
      action: "CONTENT_SUBMITTED",
      target_type: "CONTENT",
      target_id: contentId,
      metadata: { source_domain: sourceDomain, category, safety_status: safetyStatus, submitted_by_username: submitterUsername },
    }).catch((e: any) => console.warn("audit log non-fatal:", e))

    console.log(`content ${contentId} -> ${newStatus}/${safetyStatus} (${sourceDomain})`)

    return new Response(JSON.stringify({
      status: isBlocked ? "BLOCKED" : "PUBLISHED",
      content_id: contentId,
      metadata: {
        title,
        description: description ?? "",
        category,
        organizer: organizer ?? "",
        source_domain: sourceDomain,
        safety_status: safetyStatus,
      },
    }), { headers })

  } catch (error) {
    console.error("submit-content-url error:", error)
    return new Response(JSON.stringify({ error: (error as Error).message, status: "FAILED" }), {
      headers,
      status: 400,
    })
  }
})
