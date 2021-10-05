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
import android.util.Log

class FlutterWebAuthPlugin(private var context: Context? = null, private var channel: MethodChannel? = null): MethodCallHandler, FlutterPlugin {
  companion object {
    public val callbacks = mutableMapOf<String, Result>()
    public var urlScheme: String? = null
    public var tabOpened = false
    public val PACKAGE_NAME = "com.android.chrome"

    @JvmStatic
    fun registerWith(registrar: Registrar) {
        val plugin = FlutterWebAuthPlugin()
        plugin.initInstance(registrar.messenger(), registrar.context())
    }

    // MainActivity.java
    // 
    // @Override
    // protected void onResume() {
    //     FlutterWebAuthPlugin.Companion.checkTabOpened();
    //     super.onResume();
    // }
    public fun checkTabOpened() {
        if (tabOpened) {
            callbacks.remove(urlScheme)?.success("fail")
            tabOpened = false
        }
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

          val packageManager = context!!.packageManager
          val browserIntent = Intent(Intent.ACTION_VIEW, url)
          val browsersList = packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
          Log.d("TAG", "+++print browserlist+++")
          browsersList.forEach{
            val packageName = it.activityInfo.packageName
            Log.d("TAG", packageName)
          }
          Log.d("TAG", browsersList.first().activityInfo.packageName)
          intent.intent.setPackage(browsersList.first().activityInfo.packageName)
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
