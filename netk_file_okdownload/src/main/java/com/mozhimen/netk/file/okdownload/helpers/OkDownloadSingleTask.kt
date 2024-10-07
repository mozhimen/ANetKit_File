package com.mozhimen.netk.file.okdownload.helpers

import android.util.Log
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.StatusUtil
import com.liulishuo.okdownload.core.cause.EndCause
import com.liulishuo.okdownload.core.listener.DownloadListener2
import com.mozhimen.kotlin.lintk.optins.OApiCall_BindLifecycle
import com.mozhimen.kotlin.lintk.optins.OApiInit_ByLazy
import com.mozhimen.basick.taskk.bases.BaseWakeBefDestroyTaskK
import com.mozhimen.kotlin.utilk.android.os.UtilKBuildVersion
import com.mozhimen.kotlin.utilk.android.util.UtilKLogWrapper
import com.mozhimen.kotlin.utilk.commons.IUtilK
import com.mozhimen.kotlin.utilk.kotlin.isStrUrlConnectable
import com.mozhimen.kotlin.utilk.kotlin.ranges.UtilK_Ranges
import com.mozhimen.netk.file.okdownload.commons.IFileDownloadSingleListener
import java.io.File
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * @ClassName SingleFileDownloadMgr
 * @Description TODO
 * @Author Mozhimen & Kolin Zhao
 * @Date 2022/11/1 23:27
 * @Version 1.0
 */
@OApiInit_ByLazy
@OApiCall_BindLifecycle
class OkDownloadSingleTask : BaseWakeBefDestroyTaskK() {
    private val _downloadUrls = CopyOnWriteArrayList<String>()
    private var _downloadListenerMap = ConcurrentHashMap<String, IFileDownloadSingleListener>()
    private var _downloadTaskMap = ConcurrentHashMap<String, DownloadTask>()

    fun start(url: String, strFilePathName: String, listener: IFileDownloadSingleListener? = null) {
        start(url, File(strFilePathName), listener)
    }

    fun start(url: String, file: File, listener: IFileDownloadSingleListener? = null) {
        if (!url.isStrUrlConnectable() || _downloadUrls.contains(url)) return

        _downloadUrls.add(url)
        listener?.let { _downloadListenerMap[url] = it }
        val downloadTask = DownloadTask.Builder(url, file, null).build()
        downloadTask.enqueue(DownloadCallback2(listener))
        _downloadTaskMap[url] = downloadTask
    }

    private fun popupDownloadTask(url: String) {
        if (UtilKBuildVersion.isAfterV_24_7_N()) {
            _downloadUrls.removeIf { it == url }
        } else {
            _downloadUrls.remove(url)
        }
        _downloadListenerMap.remove(url)
        _downloadTaskMap.remove(url)
    }

    private fun cancelAll() {
        for (task in _downloadTaskMap) task.value.cancel()
        _downloadTaskMap.clear()
        _downloadListenerMap.clear()
        _downloadUrls.clear()
    }

    override fun isActive(): Boolean {
        return _downloadTaskMap.isNotEmpty() || _downloadUrls.isNotEmpty() || _downloadListenerMap.isNotEmpty()
    }

    override fun cancel() {
        cancelAll()
    }

    internal inner class DownloadCallback2(private val _listener: IFileDownloadSingleListener? = null) : DownloadListener2(), IUtilK {
        private var _totalIndex = 0
        private var _totalBytes = 0L
        override fun taskStart(task: DownloadTask) {
            Log.d(TAG, "taskStart...")
            Log.d(TAG, "taskStart: task Info ${getTaskInfo(task)}")
            clearProgressInfo()
            _listener?.onStart(task)
        }

        override fun fetchProgress(task: DownloadTask, blockIndex: Int, increaseBytes: Long) {
            super.fetchProgress(task, blockIndex, increaseBytes)
            _totalIndex += blockIndex
            _totalBytes += increaseBytes
            Log.v(TAG, "fetchProgress: _totalIndex $_totalIndex _totalBytes ${_totalBytes / 1024 / 1024}MB")
            _listener?.onProgress(task, UtilK_Ranges.constraint(_totalIndex, 0, 100), _totalBytes)
        }

        override fun taskEnd(task: DownloadTask, cause: EndCause, realCause: Exception?) {
            Log.d(TAG, "taskEnd...")
            //may be some problem
//            when (cause) {
//                EndCause.COMPLETED -> {
//                    val savePath: String? = task.file?.absolutePath
//                    savePath?.let {
//                        Log.d(TAG, "taskEnd: success")
//                        _listener?.onComplete(task)
//                    } ?: kotlin.run {
//                        LogK.et(TAG, "taskEnd: fail get file path fail")
//                        _listener?.onFail(task, Exception("get file path fail"))
//                    }
//                }
//                else -> {
//                    LogK.et(TAG, "taskEnd: error ${cause.name} realCause ${realCause?.message}")
//                    realCause?.printStackTrace()
//                    _listener?.onFail(task, realCause)
//                }
//            }
            if (StatusUtil.isCompleted(task)) {
                val savePath: String? = task.file?.absolutePath
                savePath?.let {
                    Log.d(TAG, "taskEnd: success")
                    _listener?.onComplete(task)
                } ?: kotlin.run {
                    UtilKLogWrapper.e(TAG, "taskEnd: fail get file path fail")
                    _listener?.onFail(task, Exception("get file path fail"))
                }
            } else {
                UtilKLogWrapper.e(TAG, "taskEnd: error ${cause.name} realCause ${realCause?.message}")
                realCause?.printStackTrace()
                _listener?.onFail(task, realCause)
            }
            popupDownloadTask(task.url)
            clearProgressInfo()
        }

        private fun getTaskInfo(task: DownloadTask): String {
            val stringBuilder = StringBuilder()
            stringBuilder.apply {
                append("priority-> ${task.priority}").append(" ")
                append("info?.filename-> ${task.info?.filename}").append(" ")
                append("info?.url-> ${task.info?.url}").append(" ")
                append("info?.blockCount-> ${task.info?.blockCount}").append(" ")
                append("info?.file?.absolutePath-> ${task.info?.file?.absolutePath}").append(" ")
                append("info?.file?.totalSpace-> ${task.info?.file?.totalSpace}").append(" ")
                append("info?.file?.totalLength-> ${task.info?.totalLength}").append(" ")
                append("task.flushBufferSize-> ${task.flushBufferSize}").append(" ")
                append("task.readBufferSize-> ${task.readBufferSize}").append(" ")
                append("task.syncBufferSize-> ${task.syncBufferSize}").append(" ")
            }
            return stringBuilder.toString()
        }

        private fun clearProgressInfo() {
            _totalBytes = 0L
            _totalIndex = 0
        }
    }
}