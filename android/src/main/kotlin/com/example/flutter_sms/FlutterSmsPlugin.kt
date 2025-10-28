package com.example.flutter_sms

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class FlutterSmsPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private val REQUEST_CODE_SEND_SMS = 205

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        setupChannel(binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    private fun setupChannel(messenger: BinaryMessenger) {
        channel = MethodChannel(messenger, "flutter_sms")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "sendSMS" -> {
                if (!canSendSMS()) {
                    result.error(
                        "device_not_capable",
                        "This device cannot send SMS messages.",
                        null
                    )
                    return
                }
                val message = call.argument<String>("message") ?: ""
                val recipients = call.argument<String>("recipients") ?: ""
                val sendDirect = call.argument<Boolean>("sendDirect") ?: false
                sendSMS(result, recipients, message, sendDirect)
            }

            "canSendSMS" -> result.success(canSendSMS())
            else -> result.notImplemented()
        }
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private fun canSendSMS(): Boolean {
        val act = activity ?: return false
        if (!act.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            return false
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        val activityInfo = intent.resolveActivityInfo(act.packageManager, intent.flags)
        return activityInfo != null && activityInfo.exported
    }

    private fun sendSMS(result: Result, phones: String, message: String, sendDirect: Boolean) {
        if (sendDirect) {
            sendSMSDirect(result, phones, message)
        } else {
            sendSMSDialog(result, phones, message)
        }
    }

    private fun sendSMSDirect(result: Result, phones: String, message: String) {
        val act = activity ?: run {
            result.error("NO_ACTIVITY", "Activity not attached", null)
            return
        }

        val sentIntent = PendingIntent.getBroadcast(
            act,
            0,
            Intent("SMS_SENT_ACTION"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val smsManager = SmsManager.getDefault()
        val numbers = phones.split(";").filter { it.isNotBlank() }

        for (num in numbers) {
            Log.d("Flutter SMS", "Sending to $num")
            if (message.toByteArray().size > 80) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(num, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(num, null, message, sentIntent, null)
            }
        }
        result.success("SMS Sent!")
    }

    private fun sendSMSDialog(result: Result, phones: String, message: String) {
        val act = activity ?: run {
            result.error("NO_ACTIVITY", "Activity not attached", null)
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phones")).apply {
            putExtra("sms_body", message)
        }
        act.startActivityForResult(intent, REQUEST_CODE_SEND_SMS)
        result.success("SMS Sent!")
    }
}
