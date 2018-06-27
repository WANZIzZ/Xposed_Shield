package com.dfsc.xposed_shield

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Fragment
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LayoutInflated
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class Main : IXposedHookLoadPackage, IXposedHookInitPackageResources {

    private val ACTION_SHOW_FOBBIDN_ACTIVITY = "action.show.fobbiden.FobbidenActivity"

    private val incall = "com.android.incallui"
    private val telecom = "com.android.server.telecom"
    private val mms = "com.android.mms"

    private val settings = "com.android.settings.MiuiSettings"                       // 设置界面
    private val search = "com.android.quicksearchbox.SearchActivity"                 // 搜索界面
    private val recordPreview = "com.android.soundrecorder.RecordPreviewActivity"    // 录音预览界面
    private val installer = "com.android.packageinstaller.PackageInstallerActivity"  // 应用安装界面
    private val inCallUi = "com.android.incallui.InCallActivity"                     // 通话界面

    lateinit var applicationContext: Context

    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam?) {
        val packageName = resparam!!.packageName

        if (incall == packageName) {
            hookSingleCallInfo(resparam)
        }
    }


    override fun handleLoadPackage(p0: XC_LoadPackage.LoadPackageParam?) {

        applicationContext = XposedHelpers.callMethod(
                XposedHelpers.callStaticMethod(
                        XposedHelpers.findClass("android.app.ActivityThread", null),
                        "currentActivityThread",
                        arrayOfNulls(0)
                ),
                "getSystemContext",
                arrayOfNulls(0)
        ) as Context

        val packageName = p0!!.packageName

        if (telecom == packageName || mms == packageName) {
            hookMissedCallAndSms(p0.classLoader)
        }

        if (incall == packageName) {
            hookNotification(p0.classLoader)
        }

        hookActivities(p0.classLoader)
    }

    /**
     * 屏蔽指定界面
     */
    private fun hookActivities(classLoader: ClassLoader) {
        XposedHelpers.findAndHookMethod(
                "android.app.Application",
                classLoader,
                "dispatchActivityResumed",
                Activity::class.java,
                object : XC_MethodHook() {

                    @SuppressLint("ResourceType")
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        // 读取File提示FileNotFoundException  Permission denied
                        try {
                            val disabled = FileUtils.instances.isDisabled()
                            val activities = FileUtils.instances.getFobbidenActivity()
                            if (disabled) {
                                val activity = param!!.args[0] as Activity
                                if (activities.contains(activity.componentName.className)) {
                                    applicationContext
                                            .startActivity(
                                                    Intent(ACTION_SHOW_FOBBIDN_ACTIVITY)
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
        )
    }

    /**
     * 屏蔽通话界面的指定控件
     */
    private fun hookSingleCallInfo(resparam: XC_InitPackageResources.InitPackageResourcesParam) {
        // 键盘按钮
        resparam.res.hookLayout(
                resparam.packageName,
                "layout",
                "call_button_fragment",
                object : XC_LayoutInflated() {

                    override fun handleLayoutInflated(liparam: LayoutInflatedParam?) {
                        // 这里要延迟一下
                        Observable
                                .timer(200, TimeUnit.MILLISECONDS)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    val ll = liparam!!.view.findViewById<LinearLayout>(
                                            liparam.res.getIdentifier(
                                                    "toolsButtonLayout",
                                                    "id",
                                                    resparam.packageName
                                            )
                                    )
                                    //           ll.visibility = View.INVISIBLE
                                }
                    }
                }
        )
        // 通话号码
        resparam.res.hookLayout(
                resparam.packageName,
                "layout",
                "single_call_info",
                object : XC_LayoutInflated() {

                    override fun handleLayoutInflated(liparam: LayoutInflatedParam?) {
                        Observable
                                .timer(200, TimeUnit.MILLISECONDS)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    // 通话号码
                                    val name = liparam!!.view.findViewById<TextView>(
                                            liparam.res.getIdentifier(
                                                    "name",
                                                    "id",
                                                    resparam.packageName
                                            )
                                    )
                                    //  name.visibility = View.INVISIBLE
                                }

                        // 位置和号码
                        val lableAndNumber = liparam!!.view.findViewById<ViewGroup>(
                                liparam.res.getIdentifier(
                                        "labelAndNumber",
                                        "id",
                                        resparam.packageName
                                )
                        )
                        // 位置和号码暂时不隐藏
                        // lableAndNumber.visibility = View.INVISIBLE
                    }
                }
        )
        // 通话页面底部工具（新建联系人、添加通话、录音）
        resparam.res.hookLayout(
                resparam.packageName,
                "layout",
                "call_tools_view",
                object : XC_LayoutInflated() {

                    override fun handleLayoutInflated(liparam: LayoutInflatedParam?) {
                        val inCallToolsPanelBottom = liparam!!.view.findViewById<LinearLayout>(
                                liparam.res.getIdentifier(
                                        "inCallToolsPanelBottom",
                                        "id",
                                        resparam.packageName
                                )
                        )
                        inCallToolsPanelBottom.visibility = View.INVISIBLE
                    }
                }
        )
    }


    /**
     * 屏蔽通话期间通知栏中的号码和来电通知
     */
    private fun hookNotification(classLoader: ClassLoader) {
        XposedHelpers.findAndHookMethod(
                inCallUi,
                classLoader,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        val clazz = param!!.thisObject.javaClass
                        log("------------------------${clazz.name}------------------------")
                        val declaredFields = clazz.declaredFields
                        for (field in declaredFields) {
                            log("DeclaredFields name:${field.name} type:${field.type}")
                        }
                        log("------------------------END------------------------")
                    }
                }
        )

        // 通过反射获取这个类下的方法和变量
        XposedHelpers.findAndHookMethod(
                "com.android.incallui.CallCardFragment",
                classLoader,
                "onViewCreated",
                View::class.java,
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        val clazz = param!!.thisObject.javaClass
                        log("------------------------${clazz.name}------------------------")
                        val declaredFields = clazz.declaredFields
                        for (field in declaredFields) {
                            log("DeclaredFields name:${field.name} type:${field.type}")
                        }
                        log("------------------------Method------------------------")
                        val methods = clazz.methods
                        for (method in methods) {
                            log("方法名:${method.name}")
                            if (method.parameterTypes.isNotEmpty()) {
                                log("参数个数:${method.parameterTypes.size}")
                                for (parameter in method.parameterTypes) {
                                    parameter.canonicalName
                                    parameter.simpleName
                                    log("参数名称:${parameter.name}")
                                }
                            }
                        }
                        log("------------------------END------------------------")
                    }
                }
        )

        // 通话号码以及联系人备注（猜测）
        // 第一个参数是通话号码
        XposedHelpers.findAndHookMethod(
                "com.android.incallui.CallCardFragment",
                classLoader,
                "setSingleCallName",
                String::class.java,
                Boolean::class.java,
                Boolean::class.java,
                String::class.java,
                Boolean::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        log("------------------------setSingleCallName------------------------")
                        log("setSingleCallName:${param!!.args[0]}  ${param.args[3]}")
                    }
                }
        )

        // 号码归属地
        XposedHelpers.findAndHookMethod(
                "com.android.incallui.CallCardFragment",
                classLoader,
                "setSingleTelocation",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        log("------------------------setSingleTelocation------------------------")
                        log("setSingleTelocation:${param!!.args[0]}")
                    }
                }
        )

        // 号码归属地左边的号码 通话页面点击键盘，这里会显示号码
        XposedHelpers.findAndHookMethod(
                "com.android.incallui.CallCardFragment",
                classLoader,
                "setSinglePhoneNumber",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        log("------------------------setSinglePhoneNumber------------------------")
                        log("setSinglePhoneNumber:${param!!.args[0]}")
                    }
                }
        )

        XposedHelpers.findAndHookMethod(
                "android.app.NotificationManager",
                classLoader,
                "notify",
                String::
                class.java,
                Int::
                class.java,
                Notification::
                class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        val notification = param!!.args[2] as Notification
                        // 从'Android N'开始，'contentView'该字段可能为空
                        val packageName =
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                    notification.contentView.`package`
                                } else {
                                    notification.contentIntent.creatorPackage
                                }
                        val bundle = notification.extras
                        if (incall == packageName) {
                            bundle.putString("android.title", "号码未知")
                        }
                    }
                }
        )
    }

    /**
     * 清除未接来电、短信通知
     */
    private fun hookMissedCallAndSms(classLoader: ClassLoader) {
        // notifyAsUser 这个方法被hide掉了
        XposedHelpers.findAndHookMethod(
                "android.app.NotificationManager",
                classLoader,
                "notifyAsUser",
                String::class.java,
                Int::class.java,
                Notification::class.java,
                UserHandle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        val notification = param!!.args[2] as Notification
                        val packageName =
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                    notification.contentView.`package`
                                } else {
                                    notification.contentIntent.creatorPackage
                                }
                        if (telecom == packageName || mms == packageName) {
                            param.result = null
                        }

                        // val bundle = notification.extras
                        // val title = bundle.get("android.title") as String
                        // val content = bundle.get("android.text") as String
                        // XposedBridge.log("notifyAsUser -> packageName:$packageName")
                        // XposedBridge.log("notifyAsUser -> title:$title")
                        // XposedBridge.log("notifyAsUser -> content:$content")
                    }
                }
        )

    }

    fun log(message: String) {
        XposedBridge.log("wanzi   $message")
    }
}