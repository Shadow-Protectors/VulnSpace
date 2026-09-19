// @ts-nocheck
// manage-head-application Edge Function
//
// APPROVE flow guarantees the approved head ALWAYS ends up with a usable
// credential:
//   - Applicant was an anonymous session user  -> their anon account is CONVERTED
//     to a permanent email account and a one-time password (OTP) is issued.
//   - Applicant email already has a real auth account -> nothing reset; the email
//     tells them to sign in with their existing password.
//   - No account at all -> a new account is created with an OTP.
// When an OTP is issued it is included in the approval email, and (as a fallback
// when email is not configured/sending fails) returned to the CALLING ADMIN in
// the response so the password can be handed over manually.
//
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "npm:@supabase/supabase-js@2"

console.log("manage-head-application Edge Function running")

// One-time password from a CSPRNG (Math.random is NOT safe for credentials)
function generateOtp(length = 10): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
  const bytes = new Uint8Array(length)
  crypto.getRandomValues(bytes)
  let out = ""
  for (let i = 0; i < length; i++) out += chars[bytes[i] % chars.length]
  return `Vuln!${out}#`
}

// profiles.username is UNIQUE — suffix randomness so two heads with the same
// full name never collide (the old code upserted full_name and silently lost
// must_change_password when it clashed).
function makeProfileUsername(fullName: string): string {
  const base = (fullName || "head").toLowerCase().replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "").slice(0, 18) || "head"
  const bytes = new Uint8Array(4)
  crypto.getRandomValues(bytes)
  const suffix = Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("")
  return `${base}-${suffix}`
}

serve(async (req: Request) => {
  const headers = {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
  }

  if (req.method === "OPTIONS") {
    return new Response("ok", { headers })
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")

    if (!supabaseUrl || !supabaseServiceKey) {
      throw new Error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY environment variables.")
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey)

    const authHeader = req.headers.get("Authorization")
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing Authorization header" }), { headers, status: 401 })
    }

    const token = authHeader.replace("Bearer ", "").trim()
    const { data: { user }, error: userError } = await supabase.auth.getUser(token)
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Invalid or expired session. Please sign out and sign in again." }), { headers, status: 401 })
    }

    // Platform-admin check is done SERVER-SIDE against the table (service role
    // bypasses RLS), never trusting the client.
    const { data: isAdmin, error: adminError } = await supabase
      .from("platform_admins")
      .select("id")
      .eq("user_id", user.id)
      .maybeSingle()

    if (adminError || !isAdmin) {
      return new Response(JSON.stringify({ error: "Only Platform Admins can perform this action" }), { headers, status: 403 })
    }

    const body = await req.json().catch(() => ({}))
    const applicationId = body.applicationId || body.application_id
    const rawAction = body.action || ""
    const action = rawAction.toUpperCase()
    const rejectionReason = body.reason || body.rejection_reason || "Does not meet community guidelines"

    if (!applicationId || !["APPROVE", "REJECT"].includes(action)) {
      return new Response(JSON.stringify({ error: "Invalid payload: applicationId and action ('APPROVE' or 'REJECT') required" }), { headers, status: 400 })
    }

    console.log(`Executing ${action} on head_applications ${applicationId} by admin ${user.id}`)

    const { data: application, error: fetchError } = await supabase
      .from("head_applications")
      .select("*")
      .eq("id", applicationId)
      .single()

    if (fetchError || !application) {
      return new Response(JSON.stringify({ error: `Application not found: ${applicationId}` }), { headers, status: 404 })
    }

    // Idempotency
    if (application.status === "APPROVED") {
      return new Response(JSON.stringify({ message: "Application is already approved", status: "APPROVED", application_id: applicationId }), { headers, status: 200 })
    }
    if (application.status === "REJECTED" && action === "REJECT") {
      return new Response(JSON.stringify({ message: "Application is already rejected", status: "REJECTED", application_id: applicationId }), { headers, status: 200 })
    }

    if (action === "APPROVE") {
      let applicantUserId: string | null = application.applicant_user_id
      const otp = generateOtp()
      let issueOtp = false

      // ---- 1. Resolve the head's auth account --------------------------------
      // Case A: application is linked to a user (usually an ANONYMOUS session
      // created by the app before submitting). Anon users have no password —
      // convert THIS account into a permanent email account + OTP.
      if (applicantUserId) {
        const { data: existing, error: byIdErr } = await supabase.auth.admin.getUserById(applicantUserId)
        const extUser = existing?.user
        const looksAnonymous = !!extUser && (extUser.is_anonymous === true || !extUser.email)

        if (byIdErr || !extUser) {
          applicantUserId = null // stale reference — fall through to email path
        } else if (looksAnonymous) {
          const { error: convErr } = await supabase.auth.admin.updateUserById(applicantUserId, {
            email: application.email,
            password: otp,
            email_confirm: true,
            user_metadata: { full_name: application.full_name },
          })
          if (convErr) throw new Error(`Failed to convert applicant account: ${convErr.message}`)
          issueOtp = true
        }
        // else: real email/password account — keep their password, just link.
      }

      // Case B/C: not linked — find or create by email.
      if (!applicantUserId) {
        let existingUser: any = null
        let page = 1
        // Paginate fully — the old code only scanned page 1.
        while (true) {
          const { data: pageData } = await supabase.auth.admin.listUsers({ page, perPage: 200 })
          const users = pageData?.users ?? []
          existingUser = users.find((u: any) => u.email?.toLowerCase() === application.email?.toLowerCase()) ?? null
          if (existingUser || users.length < 200) break
          page += 1
          if (page > 20) break
        }

        if (existingUser) {
          applicantUserId = existingUser.id
          if (existingUser.is_anonymous === true) {
            const { error: convErr } = await supabase.auth.admin.updateUserById(applicantUserId, {
              email: application.email,
              password: otp,
              email_confirm: true,
              user_metadata: { full_name: application.full_name },
            })
            if (convErr) throw new Error(`Failed to convert applicant account: ${convErr.message}`)
            issueOtp = true
          }
        } else {
          const { data: newUser, error: createError } = await supabase.auth.admin.createUser({
            email: application.email,
            password: otp,
            email_confirm: true,
            user_metadata: { full_name: application.full_name },
          })
          if (createError || !newUser?.user) {
            throw new Error(`Failed to create account for Community Head: ${createError?.message}`)
          }
          applicantUserId = newUser.user.id
          issueOtp = true
        }
      }

      console.log(`Resolved Community Head user UUID: ${applicantUserId} (otp issued: ${issueOtp})`)

      // ---- 2. Profile ----------------------------------------------------------
      const profileUsername = makeProfileUsername(application.full_name)
      try {
        await supabase.from("profiles").upsert({
          id: applicantUserId,
          username: profileUsername,
          must_change_password: issueOtp,
        }, { onConflict: "id" })
      } catch (_pErr) {
        // Fallback if must_change_password column is missing in older schemas
        await supabase.from("profiles").upsert({
          id: applicantUserId,
          username: profileUsername,
        }, { onConflict: "id" }).catch((e) => console.warn("profiles upsert fallback:", e))
      }

      // ---- 3. Community --------------------------------------------------------
      let communityId: string | null = null
      const { data: existingCommunity } = await supabase
        .from("communities")
        .select("id")
        .eq("name", application.proposed_community_name)
        .maybeSingle()

      if (existingCommunity) {
        communityId = existingCommunity.id
        await supabase
          .from("communities")
          .update({ created_by: applicantUserId, status: "ACTIVE" })
          .eq("id", communityId)
          .catch(() => supabase.from("communities").update({ created_by: applicantUserId }).eq("id", communityId))
      } else {
        const insertWithStatus = await supabase
          .from("communities")
          .insert({
            name: application.proposed_community_name,
            description: application.proposed_description || "Cybersecurity Community",
            created_by: applicantUserId,
            status: "ACTIVE",
          })
          .select("id")
          .single()

        if (insertWithStatus.error) {
          const insertBasic = await supabase
            .from("communities")
            .insert({
              name: application.proposed_community_name,
              description: application.proposed_description || "Cybersecurity Community",
              created_by: applicantUserId,
            })
            .select("id")
            .single()

          if (insertBasic.error || !insertBasic.data) {
            throw new Error(`Failed to create community: ${insertBasic.error?.message}`)
          }
          communityId = insertBasic.data.id
        } else {
          communityId = insertWithStatus.data.id
        }
      }

      console.log(`Community established with ID: ${communityId}`)

      // ---- 4. Head role ---------------------------------------------------------
      const roleTry = await supabase
        .from("community_members")
        .upsert({
          community_id: communityId,
          user_id: applicantUserId,
          username: application.full_name,
          role: "COMMUNITY_HEAD",
          status: "ACTIVE",
        }, { onConflict: "community_id, user_id" })

      if (roleTry.error) {
        console.warn("COMMUNITY_HEAD upsert failed, retrying with legacy HEAD:", roleTry.error)
        const roleTry2 = await supabase
          .from("community_members")
          .upsert({
            community_id: communityId,
            user_id: applicantUserId,
            username: application.full_name,
            role: "HEAD",
            status: "ACTIVE",
          }, { onConflict: "community_id, user_id" })

        if (roleTry2.error) {
          throw new Error(`Failed to assign community head role: ${roleTry2.error.message}`)
        }
      }

      // ---- 5. Application record -------------------------------------------------
      const updateResult = await supabase
        .from("head_applications")
        .update({
          status: "APPROVED",
          approved_by: user.id,
          approved_at: new Date().toISOString(),
          applicant_user_id: applicantUserId,
          updated_at: new Date().toISOString(),
        })
        .eq("id", applicationId)

      if (updateResult.error) {
        await supabase.from("head_applications").update({ status: "APPROVED" }).eq("id", applicationId)
      }

      // ---- 6. Audit + notification ------------------------------------------------
      await supabase.from("audit_logs").insert({
        actor_id: user.id,
        action: "COMMUNITY_HEAD_APPLICATION_APPROVED",
        target_type: "HEAD_APPLICATION",
        target_id: applicationId,
        community_id: communityId,
        metadata: {
          applicant_email: application.email,
          applicant_user_id: applicantUserId,
          community_name: application.proposed_community_name,
        },
      }).catch((e: any) => console.warn("Audit log insert non-fatal warning:", e))

      await supabase.from("notifications").insert({
        recipient_user_id: applicantUserId,
        application_id: applicationId,
        community_id: communityId,
        title: "Community application approved",
        body: "Your application was approved. Check your email for login instructions.",
        type: "HEAD_APPLICATION_APPROVED",
      }).catch((e: any) => console.warn("Notification insert non-fatal warning:", e))

      // ---- 7. Email (now actually delivers the one-time password) -----------------
      const credentialsBlock = issueOtp
        ? `\nSign-in email: ${application.email}\nOne-time password: ${otp}\n\nOpen the VulnSpace app, choose Community Head Login and sign in with these credentials. You will be asked to set your own password on first login. The one-time password stops working after that.\n`
        : `\nSign in to the VulnSpace app with your existing password for ${application.email}. Your account now has Community Head access to '${application.proposed_community_name}'.\n`

      const resendApiKey = Deno.env.get("RESEND_API_KEY")
      let emailStatus = "NOT_SENT"
      let errorMessage: string | null = null
      let providerMessageId: string | null = null

      if (resendApiKey) {
        try {
          const emailRes = await fetch("https://api.resend.com/emails", {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${resendApiKey}`,
            },
            body: JSON.stringify({
              from: "VulnSpace <no-reply@vulnspace.org>",
              to: [application.email],
              subject: "Your VulnSpace community application was approved",
              text: `Hello ${application.full_name},\n\nYour application to create the cybersecurity community '${application.proposed_community_name}' has been approved.\n${credentialsBlock}\nWelcome to VulnSpace!`,
            }),
          })
          const emailJson = await emailRes.json().catch(() => null)
          if (emailRes.ok) {
            emailStatus = "SENT"
            providerMessageId = emailJson?.id ?? null
          } else {
            emailStatus = "FAILED"
            errorMessage = emailJson?.message ?? "Email dispatch rejected by provider"
          }
        } catch (mailErr: any) {
          emailStatus = "FAILED"
          errorMessage = mailErr?.message ?? "Network error during email dispatch"
        }
      }

      await supabase.from("email_deliveries").insert({
        recipient_email: application.email,
        recipient_user_id: applicantUserId,
        application_id: applicationId,
        email_type: "HEAD_APPLICATION_APPROVED",
        status: emailStatus,
        provider_message_id: providerMessageId,
        error_message: errorMessage,
        sent_at: emailStatus === "SENT" ? new Date().toISOString() : null,
      }).catch((e: any) => console.warn("email_deliveries insert non-fatal warning:", e))

      console.log(`Approval completed for ${applicationId}; email_status=${emailStatus}`)

      // Fallback credential delivery: if (and only if) the email was not sent,
      // return the OTP to the authenticated PLATFORM ADMIN so they can pass it
      // to the head manually. Never expose this to any other caller.
      const responseBody: Record<string, unknown> = {
        message: emailStatus === "SENT"
          ? "Application approved. Sign-in instructions emailed to the applicant."
          : "Application approved, but the email was not delivered — share the one-time password with the applicant manually.",
        status: "APPROVED",
        community_id: communityId,
        applicant_user_id: applicantUserId,
        email_status: emailStatus,
      }
      if (issueOtp && emailStatus !== "SENT") {
        responseBody.one_time_password = otp
      }

      return new Response(JSON.stringify(responseBody), { headers, status: 200 })

    } else {
      // ---- REJECT ---------------------------------------------------------------
      const rejectUpdate = await supabase
        .from("head_applications")
        .update({
          status: "REJECTED",
          reviewed_by: user.id,
          reviewed_at: new Date().toISOString(),
          rejection_reason: rejectionReason,
          updated_at: new Date().toISOString(),
        })
        .eq("id", applicationId)

      if (rejectUpdate.error) {
        await supabase.from("head_applications").update({ status: "REJECTED" }).eq("id", applicationId)
      }

      await supabase.from("audit_logs").insert({
        actor_id: user.id,
        action: "COMMUNITY_HEAD_APPLICATION_REJECTED",
        target_type: "HEAD_APPLICATION",
        target_id: applicationId,
        metadata: { applicant_email: application.email, reason: rejectionReason },
      }).catch((e: any) => console.warn("Reject audit log warning:", e))

      if (application.applicant_user_id) {
        await supabase.from("notifications").insert({
          recipient_user_id: application.applicant_user_id,
          application_id: applicationId,
          title: "Community Application Update",
          body: `Your community application was not approved: ${rejectionReason}`,
        }).catch((e: any) => console.warn("Reject notification warning:", e))
      }

      console.log(`Rejection completed for application ${applicationId}`)

      return new Response(JSON.stringify({
        message: "Application rejected successfully",
        status: "REJECTED",
        application_id: applicationId,
      }), { headers, status: 200 })
    }

  } catch (error) {
    console.error("manage-head-application caught error:", error)
    return new Response(JSON.stringify({ error: (error as Error).message }), { headers, status: 400 })
  }
})
