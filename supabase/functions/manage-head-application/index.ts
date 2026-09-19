// @ts-nocheck
// @ts-ignore (Suppresses IDE warning for Deno URL imports)
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "npm:@supabase/supabase-js@2"

console.log("manage-head-application Edge Function running")

serve(async (req: Request) => {
  // CORS headers
  const headers = {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  }

  if (req.method === "OPTIONS") {
    return new Response("ok", { headers })
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")

    if (!supabaseUrl || !supabaseServiceKey) {
      throw new Error("Missing environment variables for Supabase connection.")
    }

    // Initialize the Supabase client with the service role key to bypass RLS for admin actions
    const supabase = createClient(supabaseUrl, supabaseServiceKey)

    // Extract auth header to identify the caller
    const authHeader = req.headers.get("Authorization")
    if (!authHeader) {
      throw new Error("Missing Authorization header")
    }

    // Get the user from the auth token
    const token = authHeader.replace("Bearer ", "")
    const { data: { user }, error: userError } = await supabase.auth.getUser(token)
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Invalid or expired token" }), {
        headers,
        status: 401,
      })
    }

    // Verify caller is a PLATFORM_ADMIN
    const { data: isAdmin, error: adminError } = await supabase
      .from("platform_admins")
      .select("id")
      .eq("user_id", user.id)
      .maybeSingle()

    if (adminError || !isAdmin) {
      return new Response(JSON.stringify({ error: "Unauthorized: Only Platform Admins can perform this action" }), {
        headers,
        status: 403,
      })
    }

    // Parse the request body (application ID and action: 'APPROVE' or 'REJECT')
    const { application_id, action, rejection_reason } = await req.json()

    if (!application_id || !["APPROVE", "REJECT"].includes(action)) {
      return new Response(JSON.stringify({ error: "Invalid payload: application_id and valid action required" }), {
        headers,
        status: 400,
      })
    }

    // Fetch the application
    const { data: application, error: fetchError } = await supabase
      .from("head_applications")
      .select("*")
      .eq("id", application_id)
      .single()

    if (fetchError || !application) {
      return new Response(JSON.stringify({ error: "Application not found" }), {
        headers,
        status: 404,
      })
    }

    // Idempotency: check if already resolved
    if (application.status === "APPROVED") {
      return new Response(JSON.stringify({ message: "Application is already approved", status: "APPROVED" }), {
        headers,
        status: 200,
      })
    }

    if (application.status === "REJECTED" && action === "REJECT") {
      return new Response(JSON.stringify({ message: "Application is already rejected", status: "REJECTED" }), {
        headers,
        status: 200,
      })
    }

    if (action === "APPROVE") {
      let applicantUserId = application.applicant_user_id

      // 1. If applicant has no user account, resolve or create one by email
      if (!applicantUserId) {
        // Check if an Auth user already exists with this email
        const { data: userList } = await supabase.auth.admin.listUsers()
        const existingUser = userList?.users?.find(
          (u: any) => u.email?.toLowerCase() === application.email?.toLowerCase()
        )

        if (existingUser) {
          applicantUserId = existingUser.id
        } else {
          // Generate a secure temporary password for the approved head
          const tempPassword = `VulnHead!${Math.random().toString(36).slice(-8)}#`

          const { data: newUser, error: createError } = await supabase.auth.admin.createUser({
            email: application.email,
            password: tempPassword,
            email_confirm: true,
            user_metadata: { full_name: application.full_name },
          })

          if (createError || !newUser?.user) {
            throw new Error(`Failed to create Auth account for Community Head: ${createError?.message}`)
          }
          applicantUserId = newUser.user.id
        }
      }

      // 2. Ensure profiles record exists and mark must_change_password
      await supabase.from("profiles").upsert({
        id: applicantUserId,
        username: application.full_name,
        must_change_password: true,
      }, { onConflict: "id" })

      // 3. Create the community
      const { data: newCommunity, error: communityError } = await supabase
        .from("communities")
        .insert({
          name: application.proposed_community_name,
          description: application.proposed_description,
          created_by: applicantUserId,
          status: "ACTIVE",
        })
        .select()
        .single()

      if (communityError || !newCommunity) {
        throw new Error(`Failed to create community: ${communityError?.message}`)
      }

      // 4. Assign Community Head role
      await supabase
        .from("community_members")
        .upsert({
          community_id: newCommunity.id,
          user_id: applicantUserId,
          username: application.full_name,
          role: "COMMUNITY_HEAD",
          status: "ACTIVE",
        }, { onConflict: "community_id, user_id" })

      // 5. Update application record
      await supabase
        .from("head_applications")
        .update({
          status: "APPROVED",
          approved_by: user.id,
          approved_at: new Date().toISOString(),
          applicant_user_id: applicantUserId,
          updated_at: new Date().toISOString(),
        })
        .eq("id", application_id)

      // 6. Insert Audit Log
      await supabase.from("audit_logs").insert({
        actor_id: user.id,
        action: "COMMUNITY_HEAD_APPLICATION_APPROVED",
        target_type: "HEAD_APPLICATION",
        target_id: application_id,
        community_id: newCommunity.id,
        metadata: {
          applicant_email: application.email,
          applicant_user_id: applicantUserId,
          community_name: newCommunity.name,
        },
      })

      // 7. Insert In-app Notification for the applicant
      await supabase.from("notifications").insert({
        recipient_user_id: applicantUserId,
        application_id: application_id,
        community_id: newCommunity.id,
        title: "Community application approved",
        body: "Your application was approved. Check your email for login instructions.",
        type: "HEAD_APPLICATION_APPROVED",
      })

      // 8. Handle Email notification if RESEND_API_KEY is present
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
              text: `Hello ${application.full_name},\n\nYour application to create the cybersecurity community '${newCommunity.name}' has been approved.\n\nLogin email:\n${application.email}\n\nUse the VulnSpace app to sign in. You will be prompted to set up your password during your first login.\n\nWelcome to VulnSpace!`,
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

      // Record email delivery audit
      await supabase.from("email_deliveries").insert({
        recipient_email: application.email,
        recipient_user_id: applicantUserId,
        application_id: application_id,
        email_type: "HEAD_APPLICATION_APPROVED",
        status: emailStatus,
        provider_message_id: providerMessageId,
        error_message: errorMessage,
        sent_at: emailStatus === "SENT" ? new Date().toISOString() : null,
      }).catch((e: any) => console.error("Failed to record email delivery:", e))

      return new Response(JSON.stringify({
        message: "Application approved successfully and community created",
        community_id: newCommunity.id,
        applicant_user_id: applicantUserId,
        email_status: emailStatus,
      }), { headers, status: 200 })

    } else {
      // REJECT ACTION
      await supabase
        .from("head_applications")
        .update({
          status: "REJECTED",
          approved_by: user.id,
          approved_at: new Date().toISOString(),
          rejection_reason: rejection_reason || "Does not meet community guidelines",
          updated_at: new Date().toISOString(),
        })
        .eq("id", application_id)

      // Insert Audit Log
      await supabase.from("audit_logs").insert({
        actor_id: user.id,
        action: "COMMUNITY_HEAD_APPLICATION_REJECTED",
        target_type: "HEAD_APPLICATION",
        target_id: application_id,
        metadata: {
          applicant_email: application.email,
          reason: rejection_reason,
        },
      })

      // In-app notification if user has an account
      if (application.applicant_user_id) {
        await supabase.from("notifications").insert({
          recipient_user_id: application.applicant_user_id,
          title: "Community Application Update",
          body: `Your community application was not approved: ${rejection_reason || "Please contact platform support."}`,
        })
      }

      return new Response(JSON.stringify({
        message: "Application rejected successfully",
        application_id,
      }), { headers, status: 200 })
    }

  } catch (error) {
    return new Response(JSON.stringify({ error: (error as Error).message }), {
      headers,
      status: 400,
    })
  }
})
