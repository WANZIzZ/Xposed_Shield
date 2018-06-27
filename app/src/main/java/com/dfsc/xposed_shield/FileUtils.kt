package com.dfsc.xposed_shield

import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileUtils private constructor() {

    companion object {

        val instances: FileUtils by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            FileUtils()
        }
    }

    private val settingsFile = File("${Environment.getExternalStorageDirectory().path}/Launcher_Settings.txt")
    private val visibleAppFile = File("${Environment.getExternalStorageDirectory().path}/Launcher_VisibleApp.txt")
    private val fobbidenActivityFile = File("${Environment.getExternalStorageDirectory().path}/Launcher_FobbidenActivity.txt")

    private fun create(): Boolean {
        if (!settingsFile.exists()) {
            return settingsFile.createNewFile()
        }
        if (!visibleAppFile.exists()) {
            return visibleAppFile.createNewFile()
        }
        if (!fobbidenActivityFile.exists()) {
            return fobbidenActivityFile.createNewFile()
        }
        return true
    }

    /**
     * 设置是否禁用
     */
    fun setDisabled(disabled: Boolean) {
        if (!create()) {
            return
        }

        write(settingsFile, disabled.toString())
    }

    /**
     * 是否禁用
     */
    fun isDisabled(): Boolean {
        val value = read(settingsFile)

        if (value.isNotEmpty()) {
            return value.toBoolean()
        }
        return false
    }

    /**
     * 设置可见APP
     */
    fun setVisibleApp(value: String) {
        if (!create()) {
            return
        }

        write(visibleAppFile, value)
    }

    /**
     * 获取可见APP
     */
    fun getVisibleApp(): List<String> {
        val value = read(visibleAppFile)
        if (value.isNotEmpty()) {
            return value
                    .split(",")
                    .filter {
                        return@filter it.isNotEmpty()
                    }
                    .map {
                        return@map it.trim()
                    }
        }
        return emptyList()
    }

    /**
     * 设置禁止显示的Activity
     */
    fun setFobbidenActivity(value: String) {
        if (!create()) {
            return
        }

        write(fobbidenActivityFile, value)
    }

    /**
     * 获取禁止显示的Activity
     */
    fun getFobbidenActivity(): List<String> {
        val value = read(fobbidenActivityFile)
        if (value.isNotEmpty()) {
            return value
                    .split(",")
                    .filter {
                        return@filter it.isNotEmpty()
                    }
                    .map {
                        return@map it.trim()
                    }
        }
        return emptyList()
    }

    /**
     *  写入
     */
    private fun write(file: File, data: String) {
        val fos = FileOutputStream(file)
        fos.write(data.toByteArray())
        fos.close()
    }

    /**
     *  读取
     */
    private fun read(file: File): String {
        //打开文件输入流
        val input = FileInputStream(file)
        //定义1M的缓冲区
        val temp = ByteArray(1024)
        //定义字符串变量
        val sb = StringBuilder("")
        var len = 0
        //读取文件内容，当文件内容长度大于0时，
        do {
            sb.append(String(temp, 0, len))
            len = input.read(temp)
        } while (len > 0)
        //关闭输入流
        input.close()
        //返回字符串
        return sb.toString()
    }

}