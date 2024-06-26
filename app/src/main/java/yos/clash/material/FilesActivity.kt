@file:Suppress("BlockingMethodInNonBlockingContext")

package yos.clash.material

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import yos.clash.material.common.util.grantPermissions
import yos.clash.material.common.util.ticker
import yos.clash.material.common.util.uuid
import yos.clash.material.design.FilesDesign
import yos.clash.material.design.util.showExceptionToast
import yos.clash.material.remote.FilesClient
import yos.clash.material.service.model.Profile
import yos.clash.material.util.fileName
import yos.clash.material.util.withProfile
import java.util.*
import java.util.concurrent.TimeUnit

class FilesActivity : BaseActivity<FilesDesign>() {
    override suspend fun main() {
        val uuid = intent.uuid ?: return finish()
        val profile = withProfile { queryByUUID(uuid) } ?: return finish()
        val root = uuid.toString()

        val design = FilesDesign(this)
        val client = FilesClient(this)
        val stack = Stack<String>()

        design.configurationEditable = profile.type != Profile.Type.Url
        design.fetch(client, stack, root)

        setContentDesign(design)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ActivityStop -> {
                            design.fetch(client, stack, root)
                        }

                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    try {
                        when (it) {
                            FilesDesign.Request.PopStack -> {
                                if (stack.empty()) {
                                    finish()
                                } else {
                                    stack.pop()
                                }
                            }

                            is FilesDesign.Request.OpenDirectory -> {
                                stack.push(it.file.id)
                            }

                            is FilesDesign.Request.OpenFile -> {
                                startActivityForResult(
                                    ActivityResultContracts.StartActivityForResult(),
                                    Intent(Intent.ACTION_VIEW).setDataAndType(
                                        client.buildDocumentUri(it.file.id),
                                        "text/plain"
                                    ).grantPermissions()
                                )
                            }

                            is FilesDesign.Request.DeleteFile -> {
                                client.deleteDocument(it.file.id)
                            }

                            is FilesDesign.Request.RenameFile -> {
                                val newName = design.requestFileName(it.file.name)

                                client.renameDocument(it.file.id, newName)
                            }

                            is FilesDesign.Request.ImportFile -> {
                                val hasPermission = XXPermissions.isGranted(
                                    this@FilesActivity,
                                    Permission.MANAGE_EXTERNAL_STORAGE
                                )

                                if (!hasPermission) {
                                    XXPermissions.with(this@FilesActivity)
                                        .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                                        .request(object : OnPermissionCallback {
                                            override fun onGranted(
                                                permissions: MutableList<String>,
                                                allGranted: Boolean
                                            ) {
                                                /*if (!allGranted) {
                                                    Toast.makeText(this@MainActivity, "部分权限未授予，某些功能可能无法使用", Toast.LENGTH_SHORT).show()
                                                }*/
                                                //成功
                                                launch(Dispatchers.Main) {
                                                    val uri: Uri? = startActivityForResult(
                                                        ActivityResultContracts.GetContent(),
                                                        "*/*"
                                                    )

                                                    if (uri != null) {
                                                        if (it.file == null) {
                                                            val name = design.requestFileName(
                                                                uri.fileName ?: "File"
                                                            )

                                                            client.importDocument(
                                                                stack.last(),
                                                                uri,
                                                                name
                                                            )
                                                        } else {
                                                            client.copyDocument(
                                                                it.file!!.id,
                                                                uri
                                                            )
                                                        }
                                                        design.fetch(client, stack, root)
                                                    }
                                                }
                                            }

                                            override fun onDenied(
                                                permissions: MutableList<String>,
                                                doNotAskAgain: Boolean
                                            ) {
                                                if (doNotAskAgain) {
                                                    // 如果是被永久拒绝就跳转到应用权限系统设置页面
                                                    XXPermissions.startPermissionActivity(
                                                        this@FilesActivity,
                                                        permissions
                                                    )
                                                }
                                            }
                                        })
                                    /*val granted = startActivityForResult(
                                        ActivityResultContracts.RequestPermission(),
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                    )

                                    if (!granted) {
                                        return@onReceive
                                    }*/
                                } else {
                                    val uri: Uri? = startActivityForResult(
                                        ActivityResultContracts.GetContent(),
                                        "*/*"
                                    )

                                    if (uri != null) {
                                        if (it.file == null) {
                                            val name = design.requestFileName(
                                                uri.fileName ?: "File"
                                            )
                                            client.importDocument(
                                                stack.last(),
                                                uri,
                                                name
                                            )
                                        } else {
                                            client.copyDocument(
                                                it.file!!.id,
                                                uri
                                            )
                                        }
                                        design.fetch(client, stack, root)
                                    }
                                }
                            }

                            is FilesDesign.Request.ExportFile -> {
                                val uri: Uri? = startActivityForResult(
                                    ActivityResultContracts.CreateDocument("text/plain"),
                                    it.file.name
                                )

                                if (uri != null) {
                                    client.copyDocument(uri, it.file.id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        design.showExceptionToast(e)
                    }

                    design.fetch(client, stack, root)
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        design?.requests?.trySend(FilesDesign.Request.PopStack)
    }

    private suspend fun FilesDesign.fetch(client: FilesClient, stack: Stack<String>, root: String) {
        val documentId = stack.lastOrNull() ?: root
        val files = if (stack.empty()) {
            val list = client.list(documentId)
            val config = list.firstOrNull { it.id.endsWith("config.yaml") }

            if (config == null || config.size > 0) list else listOf(config)
        } else {
            client.list(documentId)
        }

        swapFiles(files, stack.empty())
    }
}