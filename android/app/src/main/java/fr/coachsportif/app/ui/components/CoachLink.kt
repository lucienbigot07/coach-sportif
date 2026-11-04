package fr.coachsportif.app.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URLEncoder

/**
 * Ouvre le coach (claude.ai) DANS l'application : un panneau de navigateur intégré (Custom Tab) posé
 * par-dessus l'écran courant. Retour = on revient exactement où on était (séance en cours conservée).
 *
 * - Session claude.ai partagée avec Chrome : pas de reconnexion, connecteur et projets disponibles.
 * - Message prérempli via ?q= ; il est aussi copié dans le presse-papiers en secours.
 * - Le panneau est ciblé sur un navigateur pour ne pas être intercepté par l'appli Claude.
 */
object CoachLink {
    private const val BASE = "https://claude.ai/new"

    fun open(ctx: Context, message: String? = null) {
        val url = if (message.isNullOrBlank()) BASE
                  else BASE + "?q=" + URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        if (!message.isNullOrBlank()) {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Message pour le coach", message))
        }
        val colors = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(0xFF0F6B57.toInt())
            .setNavigationBarColor(0xFF0E1419.toInt())
            .build()
        val tab = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colors)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setStartAnimations(ctx, android.R.anim.slide_in_left, android.R.anim.fade_out)
            .setExitAnimations(ctx, android.R.anim.fade_in, android.R.anim.slide_out_right)
            // panneau haut (≈ 90 % de l'écran), redimensionnable : l'appli reste visible derrière
            .setInitialActivityHeightPx((ctx.resources.displayMetrics.heightPixels * 0.9).toInt(),
                CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
            .setToolbarCornerRadiusDp(16)
            .build()
        CustomTabsClient.getPackageName(ctx, null)?.let { tab.intent.setPackage(it) }
        val uri = Uri.parse(url)
        try {
            val activity = ctx.findActivity()
            if (activity != null) {
                // startActivityForResult : requis par Chrome pour l'affichage en panneau partiel
                tab.intent.data = uri
                @Suppress("DEPRECATION")
                activity.startActivityForResult(tab.intent, 4242)
            } else tab.launchUrl(ctx, uri)
        } catch (e: ActivityNotFoundException) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) // aucun navigateur compatible : ouverture classique
        }
        if (!message.isNullOrBlank())
            Toast.makeText(ctx, "Séance copiée : colle-la si le message n'apparaît pas", Toast.LENGTH_SHORT).show()
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }
}
