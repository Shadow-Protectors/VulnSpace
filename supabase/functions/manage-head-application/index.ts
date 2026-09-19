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

    // Initialize Supabase admin client with service_role key
    const supabase = createClient(supabaseUrl, supabaseServiceKey)

    // Extract auth header to identify the caller
    const authHeader = req.headers.get("Authorization")
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing Authorization header" }), {
        headers,
        status: 401,
      })
    }

    // Get user from auth token
    const token = authHeader.replace("Bearer ", "").trim()
    const { data: { user }, error: userError } = await supabase.auth.getUser(token)
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Invalid or expired token. Please sign in again." }), {
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

    // Parse request body
    const body = await req.json().catch(() => ({}))
    const applicationId = body.applicationId || body.application_id
    const rawAction = body.action || ""
    const action = rawAction.toUpperCase()
    const rejectionReason = body.reason || body.rejection_reason || "Does not meet community guidelines"

    if (!applicationId || !["APPROVE", "REJECT"].includes(action)) {
      return new Response(JSON.stringify({ error: "Invalid payload: applicationId and action ('APPROVE' or 'REJECT') required" }), {
        headers,
        status: 400,
      })
    }

    console.log(`Executing ${action} on head_applications ${applicationId} by admin ${user.id}`)

    // Fetch the application
    const { data: application, error: fetchError } = await supabase
      .from("head_applications")
      .select("*")
      .eq("id", applicationId)
      .single()

    if (fetchError || !application) {
      return new Response(JSON.stringify({ error: `Application not found: ${fetchError?.message || applicationId}` }), {
        headers,
        status: 404,
      })
    }

    // Idempotency: check if already resolved
    if (application.status === "APPROVED") {
      return new Response(JSON.stringify({ 
        message: "Application is already approved", 
        status: "APPROVED",
        application_id: applicationId
      }), {
        headers,
        status: 200,
      })
    }

    if (application.status === "REJECTED" && action === "REJECT") {
      return new Response(JSON.stringify({ 
        message: "Application is already rejected", 
        status: "REJECTED",
        application_id: applicationId
      }), {
        headers,
        status: 200,
      })
    }

    if (action === "APPROVE") {
      let applicantUserId = application.applicant_user_id

      // 1. If applicant has no user account, resolve or create one by email
      if (!applicantUserId) {
        const { data: userList } = await supabase.auth.admin.listUsers()
        const existingUser = userList?.users?.find(
          (u: any) => u.email?.toLowerCase() === application.email?.toLowerCase()
        )

        if (existingUser) {
          applicantUserId = existingUser.id
        } else {
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

      console.log(`Resolved Community Head user UUID: ${applicantUserId}`)

      // 2. Ensure profiles record exists (resilient against optional must_change_password column)
      try {
        await supabase.from("profiles").upsert({
          id: applicantUserId,
          username: application.full_name,
          must_change_password: true,
        }, { onConflict: "id" })
      } catch (_pErr) {
        // Fallback if must_change_password does not exist
        await supabase.from("profiles").upsert({
          id: applicantUserId,
          username: application.full_name,
        }, { onConflict: "id" }).catch((e) => console.warn("profiles upsert fallback:", e))
      }

      // 3. Create or activate the community
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
          .update({ 
            created_by: applicantUserId,
            status: "ACTIVE" 
          })
          .eq("id", communityId)
          .catch(() => {
            // If status column doesn't exist, update created_by only
            return supabase.from("communities").update({ created_by: applicantUserId }).eq("id", communityId)
          })
      } else {
        // Try inserting with status: ACTIVE, fallback to basic insert if status column absent
        let newCommunityData: any = null
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
          newCommunityData = insertBasic.data
        } else {
          newCommunityData = insertWithStatus.data
        }

        communityId = newCommunityData.id
      }

      console.log(`Community established with ID: ${communityId}`)

      // 4. Assign Community Head role (resilient against enum vs text for role)
      let roleAssigned = false
      const roleTry1 = await supabase
        .from("community_members")
        .upsert({
          community_id: communityId,
          user_id: applicantUserId,
          username: application.full_name,
          role: "COMMUNITY_HEAD",
          status: "ACTIVE",
        }, { onConflict: "community_id, user_id" })

      if (!roleTry1.error) {
        roleAssigned = true
      } else {
        console.warn("COMMUNITY_HEAD role upsert failed, retrying with legacy HEAD:", roleTry1.error)
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
        roleAssigned = true
      }

      // 5. Update application record to APPROVED
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
        // Fallback update with minimal columns if approved_by/at don't exist
        await supabase
          .from("head_applications")
          .update({ status: "APPROVED" })
          .eq("id", applicationId)
      }

      // 6. Insert Audit Log (non-blocking)
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

      // 7. Insert In-app Notification for applicant (non-blocking)
      await supabase.from("notifications").insert({
        recipient_user_id: applicantUserId,
        application_id: applicationId,
        community_id: communityId,
        title: "Community application approved",
        body: "Your application was approved. Check your email for login instructions.",
        type: "HEAD_APPLICATION_APPROVED",
      }).catch((e: any) => console.warn("Notification insert non-fatal warning:", e))

      // 8. Handle Email notification (provider failure does NOT rollback approval)
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
              text: `Hello ${application.full_name},\n\nYour application to create the cybersecurity community '${application.proposed_community_name}' has been approved.\n\nLogin email:\n${application.email}\n\nUse the VulnSpace app to sign in. You will be prompted to set up your password during your first login.\n\nWelcome to VulnSpace!`,
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

      // Record email delivery audit (non-blocking)
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

      console.log(`Approval completed successfully for application ${applicationId}`)

      return new Response(JSON.stringify({
        message: emailStatus === "FAILED" 
          ? "Application approved, but email delivery failed." 
          : "Application approved successfully and community created",
        status: "APPROVED",
        community_id: communityId,
        applicant_user_id: applicantUserId,
        email_status: emailStatus,
      }), { headers, status: 200 })

    } else {
      // REJECT ACTION
      const rejectUpdate = await supabase
        .from("head_applications")
        .update({
          status: "REJECTED",
          approved_by: user.id,
          approved_at: new Date().toISOString(),
          rejection_reason: rejectionReason,
          updated_at: new Date().toISOString(),
        })
        .eq("id", applicationId)

      if (rejectUpdate.error) {
        await supabase
          .from("head_applications")
          .update({ status: "REJECTED" })
          .eq("id", applicationId)
      }

      // Insert Audit Log (non-blocking)
      await supabase.from("audit_logs").insert({
        actor_id: user.id,
        action: "COMMUNITY_HEAD_APPLICATION_REJECTED",
        target_type: "HEAD_APPLICATION",
        target_id: applicationId,
        metadata: {
          applicant_email: application.email,
          reason: rejectionReason,
        },
      }).catch((e: any) => console.warn("Reject audit log warning:", e))

      // In-app notification if applicant has user account (non-blocking)
      if (application.applicant_user_id) {
        await supabase.from("notifications").insert({
          recipient_user_id: application.applicant_user_id,
          application_id: applicationId,
          title: "Community Application Update",
          body: `Your community application was not approved: ${rejectionReason}`,
        }).catch((e: any) => console.warn("Reject notification warning:", e))
      }

      console.log(`Rejection completed successfully for application ${applicationId}`)

      return new Response(JSON.stringify({
        message: "Application rejected successfully",
        status: "REJECTED",
        application_id: applicationId,
      }), { headers, status: 200 })
    }

  } catch (error) {
    console.error("manage-head-application caught error:", error)
    return new Response(JSON.stringify({ error: (error as Error).message }), {
      headers,
      status: 400,
    })
  }
})
