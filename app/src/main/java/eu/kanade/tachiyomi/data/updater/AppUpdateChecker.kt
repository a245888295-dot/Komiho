package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import uy.kohesive.injekt.injectLazy

class AppUpdateChecker {

    private val getApplicationRelease: GetApplicationRelease by injectLazy()

    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): GetApplicationRelease.Result {
        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    // SY -->
                    isPreviewBuildType,
                    // SY <--
                    BuildConfig.COMMIT_COUNT.toInt(),
                    BuildConfig.VERSION_NAME,
                    GITHUB_REPO,
                    forceCheck,
                ),
            )

            when (result) {
                is GetApplicationRelease.Result.NewUpdate -> AppUpdateNotifier(context).promptUpdate(result.release)
                else -> {}
            }

            result
        }
    }
}

val GITHUB_REPO: String by lazy {
    // SY -->
    // Komiho (2026-10-21): 检查更新指向本分支自己的仓库（a245888295-dot/Komiho），
    // 不再看上游 ruzhe85/Komiho —— 否则检查到的是原作者的版本，装了也会被当成「非本分支」。
    if (isPreviewBuildType) {
        "a245888295-dot/Komiho"
    } else {
        "a245888295-dot/Komiho"
    }
    // SY <--
}
