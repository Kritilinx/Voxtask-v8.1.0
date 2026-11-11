package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// Contract keys defined by Firebase SDK & runtime test harness
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Authoritative Firebase App Check initialization helper.
 * Configures DebugAppCheckProviderFactory in debug variant with synchronous .commit(),
 * and RecaptchaAppCheckProviderFactory in release variant.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
  try {
    var app = FirebaseApp.getApps(context).firstOrNull()
    if (app == null) {
      try {
        app = FirebaseApp.initializeApp(context)
      } catch (e: Exception) {
        Log.w(TAG, "FirebaseApp.initializeApp(context) from resources failed: ${e.message}")
      }
    }

    if (app == null) {
      try {
        val options = com.google.firebase.FirebaseOptions.Builder()
          .setApplicationId(context.packageName)
          .setProjectId("voxtask-aistudio")
          .setApiKey("AIzaSyDummyKeyForLocalInitialization00")
          .build()
        app = FirebaseApp.initializeApp(context, options)
      } catch (e: Exception) {
        Log.w(TAG, "Fallback Firebase initialization failed: ${e.message}")
      }
    }

    if (app == null || FirebaseApp.getApps(context).isEmpty()) {
      Log.w(TAG, "FirebaseApp is not initialized in this process. Skipping App Check configuration.")
      return
    }

    if (BuildConfig.DEBUG) {
      val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

      if (!debugToken.isNullOrEmpty()) {
        val persistenceKey = app.persistenceKey
        val prefs = context.getSharedPreferences(
          "$DEBUG_STORE_PREFIX$persistenceKey",
          Context.MODE_PRIVATE
        )
        // CRITICAL: Synchronous commit() to prevent race conditions during debug provider startup
        prefs.edit()
          .putString(DEBUG_SECRET_KEY, debugToken)
          .commit()
        Log.d(TAG, "App Check debug secret configured successfully.")
      } else {
        Log.w(TAG, "No debug token found in Intent extra.")
      }

      FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
      )
      Log.d(TAG, "DebugAppCheckProviderFactory installed.")
    } else {
      val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
      if (resId != 0) {
        val siteKey = context.getString(resId)
        if (siteKey.isNotBlank()) {
          FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
          )
          Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
        } else {
          Log.e(TAG, "recaptcha_site_key resource string is blank.")
        }
      } else {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
      }
    }
  } catch (e: Exception) {
    Log.e(TAG, "Failed to initialize Firebase App Check", e)
  }
}
