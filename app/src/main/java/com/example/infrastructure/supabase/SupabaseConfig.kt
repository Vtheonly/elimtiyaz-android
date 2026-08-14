package com.example.infrastructure.supabase

/**
 * Hard-coded Supabase configuration constants for the El-Imtiyaz app.
 *
 * These are the REAL values for the production Supabase project
 * (hkvkefubghbbotgnteir). They are intentionally hard-coded into the APK
 * for the current iteration per the user's explicit instruction.
 *
 * The `service_role` / secret key is NEVER included here — only the
 * anon/publishable key (RLS-enforced server-side).
 *
 * The `DEFAULT_TENANT_ID` is the UUID of the "El-Imtiyaz Boumerdès" tenant
 * row in the `tenants` table. It is used as a fallback when no session is
 * active yet (e.g. during the initial Supabase pull triggered by
 * `AppNavViewModel.init` before sign-in completes). After sign-in, the
 * tenant_id from `user_profiles` is used instead.
 */
object SupabaseConfig {
    /**
     * The UUID of the El-Imtiyaz Boumerdès tenant.
     *
     * This is a real UUID stored in the `tenants.id` column. The previous
     * code used the placeholder string `"ten-elimtiyaz-001"` which does NOT
     * match the column type (`uuid`) and caused every `pull_*_for_sync`
     * RPC to fail with:
     *   `22P02: invalid input syntax for type uuid: "ten-elimtiyaz-001"`
     */
    const val DEFAULT_TENANT_ID: String = "00000000-0000-0000-0000-000000000001"

    /** Real Supabase project URL. */
    const val SUPABASE_URL: String = "https://hkvkefubghbbotgnteir.supabase.co"

    /** Real anon (publishable) key — safe to embed in the APK. */
    const val SUPABASE_ANON_KEY: String =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhrdmtlZnViZ2hiYm90Z250ZWlyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODUwMDQ2ODQsImV4cCI6MjEwMDU4MDY4NH0.GDQiKjp4YBbCpsgoJXeSUqUT8Ag67He2fmngy6NNPmk"
}
