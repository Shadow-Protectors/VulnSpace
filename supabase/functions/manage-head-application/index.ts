// @ts-nocheck
// @ts-ignore (Suppresses IDE warning for Deno URL imports)
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "npm:@supabase/supabase-js@2"

console.log("Hello from manage-head-application!")

serve(async (req: Request) => {
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")

    if (!supabaseUrl || !supabaseServiceKey) {
      throw new Error("Missing environment variables for Supabase connection.")
    }

    // Initialize the Supabase client with the service role key to bypass RLS for admin actions
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

    // Verify caller is a PLATFORM_ADMIN
    const { data: isAdmin, error: adminError } = await supabase
      .from('platform_admins')
      .select('id')
      .eq('user_id', user.id)
      .single()

    if (adminError || !isAdmin) {
      return new Response(JSON.stringify({ error: "Unauthorized: Only Platform Admins can perform this action" }), {
        headers: { "Content-Type": "application/json" },
        status: 403,
      })
    }

    // Parse the request body (application ID and action: 'APPROVE' or 'REJECT')
    const { application_id, action, rejection_reason } = await req.json()

    if (!application_id || !['APPROVE', 'REJECT'].includes(action)) {
      throw new Error("Invalid payload")
    }

    // Fetch the application
    const { data: application, error: fetchError } = await supabase
      .from('head_applications')
      .select('*')
      .eq('id', application_id)
      .single()
      
    if (fetchError || !application) throw new Error("Application not found")

    if (action === 'APPROVE') {
      // 1. Update application status
      await supabase
        .from('head_applications')
        .update({ status: 'APPROVED', reviewed_by: user.id, reviewed_at: new Date().toISOString() })
        .eq('id', application_id)

      // 2. Create the community as requested by the head
      const { data: newCommunity, error: communityError } = await supabase
        .from('communities')
        .insert({
          name: application.proposed_community_name,
          description: application.proposed_description,
          created_by: application.applicant_user_id,
          status: 'ACTIVE'
        })
        .select()
        .single()

      if (communityError) throw new Error("Failed to create community")

      // 3. Make them the HEAD of the new community
      await supabase
        .from('community_members')
        .insert({
          community_id: newCommunity.id,
          user_id: application.applicant_user_id,
          username: application.full_name, // Using their real name or letting them pick later
          role: 'HEAD',
          status: 'ACTIVE'
        })

      // 4. Log the audit action
      await supabase.from('audit_logs').insert({
        actor_id: user.id,
        action: 'APPROVED_HEAD_APPLICATION',
        target_type: 'USER',
        target_id: application.applicant_user_id,
        metadata: { community_id: newCommunity.id }
      })

      return new Response(JSON.stringify({ message: "Application approved and community created" }), {
        headers: { "Content-Type": "application/json" },
      })
    } else {
      // Reject
      await supabase
        .from('head_applications')
        .update({ status: 'REJECTED', reviewed_by: user.id, reviewed_at: new Date().toISOString(), rejection_reason })
        .eq('id', application_id)

      return new Response(JSON.stringify({ message: "Application rejected" }), {
        headers: { "Content-Type": "application/json" },
      })
    }
  } catch (error) {
    return new Response(JSON.stringify({ error: (error as Error).message }), {
      headers: { "Content-Type": "application/json" },
      status: 400,
    })
  }
})
