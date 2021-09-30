package com.linusu.flutter_web_auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.text.TextUtils

import androidx.browser.customtabs.CustomTabsIntent

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

class FlutterWebAuthPlugin(private var context: Context? = null, private var channel: MethodChannel? = null): MethodCallHandler, FlutterPlugin {
  companion object {
    public val callbacks = mutableMapOf<String, Result>()
    public var urlScheme: String? = null
    public var tabOpened = false
    public val PACKAGE_NAME = "com.android.chrome"
    public PackageManager packageManager = context.getPackageManager()
    List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(customTabsIntent.intent, PackageManager.MATCH_DEFAULT_ONLY)

    @JvmStatic
    fun registerWith(registrar: Registrar) {
        val plugin = FlutterWebAuthPlugin()
        plugin.initInstance(registrar.messenger(), registrar.context())
    }

  }

  fun initInstance(messenger: BinaryMessenger, context: Context) {
      this.context = context
      channel = MethodChannel(messenger, "flutter_web_auth")
      channel?.setMethodCallHandler(this)
  }

  override public fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
      initInstance(binding.getBinaryMessenger(), binding.getApplicationContext())
  }

  override public fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
      context = null
      channel = null
  }

  override fun onMethodCall(call: MethodCall, resultCallback: Result) {
    when (call.method) {
        "authenticate" -> {
          val url = Uri.parse(call.argument("url"))
          val callbackUrlScheme = call.argument<String>("callbackUrlScheme")!!
          val preferEphemeral = call.argument<Boolean>("preferEphemeral")!!

          callbacks[callbackUrlScheme] = resultCallback
          urlScheme = callbackUrlScheme

          val intent = CustomTabsIntent.Builder().build()
          val keepAliveIntent = Intent(context, KeepAliveService::class.java)

          val packageManager = context?.packageManager
          val resolveInfoList = packageManager?.queryIntentActivities(intent.intent, PackageManager.MATCH_DEFAULT_ONLY)

          for (resolveInfo in resolveInfoList.orEmpty()) {
              val packageName = resolveInfo.activityInfo.packageName
              if (TextUtils.equals(packageName, PACKAGE_NAME))
                  intent.intent.setPackage(PACKAGE_NAME)
          }

          intent.intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
          if (preferEphemeral) {
              intent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
          }
          intent.intent.putExtra("android.support.customtabs.extra.KEEP_ALIVE", keepAliveIntent)
          tabOpened = true
          intent.launchUrl(context, url)
        }
        "cleanUpDanglingCalls" -> {
          callbacks.forEach{ (_, danglingResultCallback) ->
              danglingResultCallback.error("CANCELED", "User canceled login", null)
          }
          callbacks.clear()
          resultCallback.success(null)
        }
        else -> resultCallback.notImplemented()
    }
  }
}
