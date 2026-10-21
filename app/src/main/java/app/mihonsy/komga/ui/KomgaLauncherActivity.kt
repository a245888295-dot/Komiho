package app.mihonsy.komga.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import app.mihonsy.komga.data.KomgaPreferences
import app.mihonsy.komga.data.smb.SmbConnectionStore
import app.mihonsy.komga.data.webdav.WebDavConnectionStore
import tachiyomi.domain.storage.service.StoragePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Komga client launcher (M3-1): app entry point that routes to the main
 * tabbed activity when any source is usable, otherwise to the onboarding
 * welcome screen. Replaces Mihon's MainActivity as the MAIN/LAUNCHER target.
 *
 * SY --> Komiho Onboarding: 无任何可用来源时不再强制 KomgaConnectActivity
 * （Komga 已是可选来源），改进欢迎页让用户三选一（Komga/WebDAV/本地）或跳过。
 * SY <--
 */
class KomgaLauncherActivity : KomgaBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = KomgaPreferences(applicationContext)
        val storagePreferences = Injekt.get<StoragePreferences>()
        // 任一来源可用（Komga 已连接 / 本地根目录已设 / WebDAV 有连接 / SMB 有连接）→ 直接进主界面。
        // SY --> Komiho：旧逻辑只认 Komga 连接 + 本地根目录，漏了 WebDAV/SMB，
        // 导致只配了这两类来源的设备每次冷启动都误判为「无来源」而进欢迎页（三号机实测）。
        //
        // Komiho (2026-10-21): 本地源的口径要和 LocalSourceFileSystem.getBaseDirectory() 对齐。
        // 旧逻辑只看 localSourceRoot（SAF 选目录），漏了另外两种「本地已经可用」的情况：
        // ① 已授予「所有文件访问」——此时浏览根直接落在存储卷根，localSourceRoot 可以为空；
        // ② 用户通过浏览页切换过根目录（localBrowseRootPath）。
        // 症状：欢迎页上本地明明显示「已就绪」，冷启动却每次都再进欢迎页要用户重选来源。
        val hasFullStorageAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        val hasAnySource = prefs.hasConnection()
            || storagePreferences.localSourceRoot.get().isNotBlank()
            || storagePreferences.localBrowseRootPath.get().isNotBlank()
            || hasFullStorageAccess
            || WebDavConnectionStore.all().isNotEmpty()
            || SmbConnectionStore.all().isNotEmpty()
        val target = if (hasAnySource) {
            KomgaMainActivity::class.java
        } else {
            OnboardingActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
