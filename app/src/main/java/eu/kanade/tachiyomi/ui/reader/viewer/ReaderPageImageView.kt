package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import coil3.BitmapImage
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.ViewSizeResolver
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.cropBordersAggressive
import eu.kanade.tachiyomi.data.coil.customDecoder
import eu.kanade.tachiyomi.data.coil.enhanced
import eu.kanade.tachiyomi.data.coil.originalSizeDisplay
import eu.kanade.tachiyomi.data.coil.pageIndex
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.EnhanceTimings
import eu.kanade.tachiyomi.util.system.EinkMotion
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import eu.kanade.tachiyomi.util.waifu2x.Waifu2x
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    // MihonSY: the alwaysDecodeLongStripWithSSIV preference no longer gates the decode
    // path — every mode decodes through Coil into a software bitmap so image
    // enhancement applies everywhere.

    private var pageView: View? = null

    private var config: Config? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /**
     * Komiho: 纸化背景档位下，在页面内容之上乘一层纸纹（羊皮纸/牛皮纸），
     * 让白场漫画有纸的颗粒感。放在 dispatchDraw 里而不是 onDraw——本类是
     * FrameLayout，画面由子 View（SSIV / PhotoView / GIF）绘制，纹理必须盖在
     * 子视图之上。非纸档位零开销（[PaperPageTexture.drawOver] 直接返回）。
     */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        PaperPageTexture.drawOver(canvas, width, height)
    }

    /**
     * MihonSY: the pixel height of the loaded image, or 0 if unknown. Exposed so the
     * webtoon holder can match the item height to the real 1:1 image height when
     * "original resolution" is enabled (avoids the black gap below each strip).
     */
    val imageSHeight: Int
        get() = (pageView as? SubsamplingScaleImageView)?.sHeight ?: 0

    /**
     * For automatic background. Will be set as background color when [onImageLoaded] is called.
     */
    var pageBackground: Drawable? = null

    /**
     * Komiho 诊断：当前页序号（-1 = 未知），由 holder 在 setImage 前写入。
     * 只用于增强日志区分请求来源（`prewarm#N` / `holder#N`），不参与任何渲染逻辑。
     */
    var pageIndex: Int = -1

    /**
     * Komiho: 标记此页由系统渲染兜底产出位图（如 PDF 渲染页），无需再做图像增强。
     * 由 holder 在调用 setImage 时通过参数写入，避免对已成位图的页做无谓 2x 放大。
     */
    var skipEnhance: Boolean = false

    /**
     * Komiho (2026-10-13): 当前屏上图是否**真出过增强图**。false = 未增强
     * （被抢占打断 / 增强失败 / 门禁跳过）。预解码位图路径由 holder 置位
     * （见 `PagerPageHolder.setImage` 的 bitmap 分支），现场解码路径由
     * [onDecodeCompleted] 置位，两条路径汇聚到这一个字段。
     */
    internal var currentImageEnhanced: Boolean = true

    /**
     * Komiho (2026-10-13): 现场解码（Coil 链）完成、增强结果已知后的回执。
     * 供 holder 做「停顿后补算」的迟到判定——ViewPager 停顿回调到达时，
     * 预热抢占可见页推理的 abort 可能还没发生，只能在这里补上。
     *
     * @param enhanced 本次是否真出了增强图；@param aborted 是否被抢占打断
     * （无成功计时、无 skip 标记，但原生有打断记录）。
     */
    internal var onDecodeCompleted: ((enhanced: Boolean, aborted: Boolean) -> Unit)? = null

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
    }

    @CallSuper
    open fun onImageLoadError(error: Throwable?) {
        onImageLoadError?.invoke(error)
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)
    }

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    open fun onPageSelected(forward: Boolean) {
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            this@ReaderPageImageView.onImageLoadError(e)
                        }
                    },
                )
            }
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        val config = config
        if (config != null &&
            config.landscapeZoom &&
            config.minimumScaleType == SCALE_TYPE_CENTER_INSIDE &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            handler?.postDelayed(500) {
                val point = when (config.zoomStartPosition) {
                    ZoomStartPosition.LEFT -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                    ZoomStartPosition.RIGHT -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                    ZoomStartPosition.CENTER -> center
                }

                val targetScale = height.toFloat() / sHeight.toFloat()
                (animateScaleAndCenter(targetScale, point) ?: return@postDelayed)
                    .withDuration(500)
                    .withEasing(EASE_IN_OUT_QUAD)
                    .withInterruptible(true)
                    .start()
            }
        }
    }

    fun setImage(drawable: Drawable, config: Config) {
        this.config = config
        // MihonSY: hide any leftover enhancement status from the previous image.
        enhanceStatusView?.visibility = View.GONE
        if (drawable is Animatable) {
            prepareAnimatedImageView()
            setAnimatedImage(drawable, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(drawable, config)
        }
    }

    fun setImage(source: BufferedSource, isAnimated: Boolean, config: Config, skipEnhance: Boolean = false) {
        this.config = config
        this.skipEnhance = skipEnhance
        // MihonSY: hide any leftover enhancement status from the previous image.
        enhanceStatusView?.visibility = View.GONE
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    fun recycle() = pageView?.let {
        when (it) {
            is SubsamplingScaleImageView -> it.recycle()
            is AppCompatImageView -> it.dispose()
        }
        it.isVisible = false
    }

    // MihonSY -->
    /**
     * MihonSY: small overlay at the bottom-left showing the image enhancement state.
     * DSH (2026-10-16): 只有两种文案 —— 未出引擎结果时是「增强中…」，出图后是 `NPU OK 耗时 尺寸`；
     * 「跳过」不再出现在画面上（原因只写 DiagLog）。
     * Created lazily and only when the "show enhancement status" toggle is on.
     */
    private var enhanceStatusView: TextView? = null

    /**
     * DSH: 角标当前的**完整**文案（折叠态显示的是它的精简版）。
     * 点角标折叠/展开时不用重算状态，直接按这个字符串重画。
     */
    private var lastBadgeFullText: String? = null

    /**
     * DSH (2026-10-16 重做): 「增强中…」的语义 —— 填住「请求增强 → NPU 真出图」这一段空白。
     *
     * 上一版的错在挂载点：提示挂在 Coil 入队那一刻，而真正耗时的 NPU 计算发生在**入队之前**
     * 的预解码里（[PagerPagePreparer.preparePure] → 同步 `imageLoader.execute()`），
     * 于是黑屏那段反而没有任何提示；整屏遮罩又是第二个提示源，且只在增强成功时才撤，
     * 失败/跳过时永久留着，和角标文案叠在一起。
     *
     * 现在只保留这一个提示源（左下角，就是 NPU OK 那个位置）：
     * 请求开始 → [showEnhancementPending] 点亮「增强中…」；只有真拿到引擎出图，
     * [showEnhancementOutcome] 才换成 `NPU OK 耗时 尺寸`，其余情况一律继续显示「增强中…」。
     */
    internal fun showEnhancementPending() {
        applyEnhancementBadge("增强中…")
    }

    /** 页面根本没走增强（用户对单页关掉增强 / 关闭增强档位）时不留痕迹。 */
    internal fun hideEnhancementStatus() {
        enhanceStatusView?.visibility = View.GONE
    }

    /**
     * DSH: 这一页最终没能超分（补算次数用尽 / 没有可重算的源）。
     * 原图照常显示，角标在 NPU OK 那个位置改说「增强失败」——
     * 正常情况极少出现（用户实测约一百页遇不到一次）。
     */
    internal fun showEnhancementFailed() {
        applyEnhancementBadge("增强失败")
    }

    /**
     * DSH: 角标只有这一个写入口 —— 统一处理「折叠/展开」「增强关掉时不留残影」「压在最上层」。
     *
     * 折叠态(默认)只显示一小块 `AI` / `AI…` / `AI!`，点一下角标展开成完整文案
     * (`NPU OK 1.2s 2160×3240`)，再点一下收回去；状态存在 [ReaderPreferences.enhancementBadgeExpanded]。
     */
    private fun applyEnhancementBadge(full: String) {
        lastBadgeFullText = full
        if (skipEnhance || !enhancementStatusEnabled()) {
            // 增强被关掉：把旧文案收干净，别留一个骗人的「NPU OK」在那
            hideEnhancementStatus()
            return
        }
        val tv = ensureEnhanceStatusView()
        tv.text = if (Injekt.get<ReaderPreferences>().enhancementBadgeExpanded.get()) full else compactBadgeText(full)
        tv.visibility = View.VISIBLE
        tv.bringToFront()
    }

    /** 折叠态文案：只说「哪个引擎 / 在跑 / 失败」，不占画面。 */
    private fun compactBadgeText(full: String): String = when {
        full == "增强中…" -> "AI…"
        full == "增强失败" -> "AI!"
        Injekt.get<ReaderPreferences>().enhancementMode.get() == 5 -> "AI"
        else -> "CPU"
    }

    /** 点角标 = 折叠/展开切换（设置持久化，下一页也照这个来）。 */
    private fun toggleEnhancementBadge() {
        val preferences = Injekt.get<ReaderPreferences>()
        preferences.enhancementBadgeExpanded.set(!preferences.enhancementBadgeExpanded.get())
        lastBadgeFullText?.let { applyEnhancementBadge(it) }
    }

    /**
     * DSH: 角标自愈 —— 用户在阅读器里改了增强设置（或关了又打开）之后，
     * 这一页可能既不重新渲染、也不再重发状态，角标就空着/停旧文案（用户实测「刚关超分那一页」）。
     * 由 holder 在挂载、翻页停顿、设置变更后调用，按**当前**设置重算一次。
     */
    internal fun refreshEnhancementBadgeState(enhanced: Boolean) {
        if (skipEnhance || !enhancementStatusEnabled()) {
            hideEnhancementStatus()
            return
        }
        if (enhanced) {
            // 这页确实增强过：保留现有文案（没有文案就不补，等正常渲染路径给）
            return
        }
        applyEnhancementBadge("增强中…")
    }

    private fun enhancementStatusEnabled(): Boolean {
        val preferences = Injekt.get<ReaderPreferences>()
        return preferences.enhancementMode.get() != 0 && preferences.showEnhancementStatus.get()
    }

    private fun ensureEnhanceStatusView(): TextView {
        enhanceStatusView?.let { return it }
        val tv = TextView(context).apply {
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt()) // white text
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            alpha = 1f
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x99000000.toInt()) // dark translucent background for contrast on white pages
                cornerRadius = dpToPx(4f).toFloat()
            }
            setPadding(dpToPx(6f), dpToPx(2f), dpToPx(6f), dpToPx(2f))
            // DSH: 页面图是**后** addView 进来的（prepareNonAnimatedImageView → addView(pageView)），
            // 同一个 ViewGroup 里默认「后加的画在上层」，会把角标整个盖住。
            // 给角标一个正的 Z（elevation），ViewGroup 就按 Z 排序绘制 —— 谁后加都不影响它在最上层。
            elevation = dpToPx(6f).toFloat()
            // DSH: 点角标 = 折叠/展开完整文案。
            isClickable = true
            setOnClickListener { toggleEnhancementBadge() }
            visibility = View.GONE
        }
        val lp = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            marginStart = dpToPx(8f)
            bottomMargin = dpToPx(8f)
        }
        addView(tv, lp)
        enhanceStatusView = tv
        return tv
    }

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

    /**
     * MihonSY: shows the enhancement outcome badge. Success shows which engine actually ran
     * plus the real elapsed time; 还没出引擎结果时是「增强中…」。No-op unless enhancement is on
     * AND the status toggle is on. Enhancement itself runs synchronously inside the Coil
     * decoder, so this is only called from the Coil success/error listeners — plus
     * PagerPageHolder for the pre-decoded bitmap path (SY: Page 优化，耗时来自预处理阶段).
     *
     * Komiho (2026-09-17): the success label reports the **engine that really produced the
     * image** — `NPU OK` / `GPU OK` / `CPU OK` — read from [Waifu2x.engineFor] rather than
     * from the selected model. A silent NPU→Vulkan fallback used to render identically to a
     * genuine NPU run, which made the cross-HTP experiment unreadable from the screen.
     *
     * Komiho (2026-09-19): it is looked up **by page index**. Reading the single global slot
     * meant a concurrent page's fallback could mislabel this page (`CPU OK` on a page that ran
     * on the NPU); see [Waifu2x.engineFor].
     */
    internal fun showEnhancementOutcome(
        success: Boolean,
        elapsedMillis: Long,
        output: String? = null,
        // DSH: 没出引擎结果的原因。只写日志（DiagLog），不显示给用户 ——
        // 「跳过·已够清晰」这类字样已经取消，用户只要知道「还在增强中」。
        reason: String? = null,
    ) {
        reason?.let {
            eu.kanade.tachiyomi.diagnostic.DiagLog.d("Waifu2xBadge", "page=$pageIndex 未出引擎结果: $it")
        }
        // DSH: 只有「真·引擎出图」才算完成。AI 档下引擎为 NONE（没出结果 / CPU 无兜底）
        // 一律回到「增强中…」，等补算或下一次请求出图后再换 NPU OK。
        val engineOk = success &&
            (
                Injekt.get<ReaderPreferences>().enhancementMode.get() != 5 ||
                    Waifu2x.engineFor(pageIndex) != Waifu2x.EngineKind.NONE
                )
        val full = if (engineOk) {
            String.format(
                java.util.Locale.US,
                "%s %.1fs%s",
                engineLabel(pageIndex),
                elapsedMillis / 1000f,
                // Komiho (2026-09-26): 附输出分辨率（如 1080×1620）—— 降采样核/开关排障时
                // 直接看最终出图尺寸，不用再猜链路走到哪一步。
                output?.let { " $it" }.orEmpty(),
            )
        } else {
            "增强中…"
        }
        applyEnhancementBadge(full)
    }

    /**
     * Komiho: engine tag for the badge. CPU 档（Lanczos3 / Catmull-Rom）本身就是 CPU，直接标
     * `CPU OK`；AI 档要看原生侧真正跑的是哪台引擎 —— 见 [Waifu2x.engineFor]。
     *
     * 2026-09-19：按**页号**取引擎，而不是读全局 `lastEngine`。增强是并发跑的（预取 + 当前页），
     * 全局槽会被别的页覆盖 —— 曾导致「明明跑的是 NPU，角标却显示 CPU OK」。
     */
    private fun engineLabel(pageIndex: Int): String =
        when (Injekt.get<ReaderPreferences>().enhancementMode.get()) {
            // CPU resampler modes (2 = Lanczos3, 3 = Catmull-Rom) never touch the native engines.
            2, 3 -> "CPU OK"
            else -> when (Waifu2x.engineFor(pageIndex)) {
                Waifu2x.EngineKind.QNN_HTP -> "NPU OK"
                Waifu2x.EngineKind.NCNN_VULKAN -> "GPU OK"
                // NONE = 引擎没出结果。AI 档下 DSH 已取消 CPU 兜底，此时处于「等待重试」状态，
                // 角标直说「NPU 重试」而不是谎报 CPU OK，方便用户判断是否真的卡住。
                Waifu2x.EngineKind.NONE -> if (Injekt.get<ReaderPreferences>().enhancementMode.get() == 5) "NPU 重试" else "CPU OK"
            }
        }
    // MihonSY <--

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->

            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setMaxTileSize(ImageUtil.hardwareBitmapThreshold)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        // Not used
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        // 5x zoom
        maxScale = scale * MAX_ZOOM_SCALE
        setDoubleTapZoomScale(scale * 2)

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(scale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(scale, center)
            null -> {}
        }
    }

    private fun setNonAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1) // Just so that very small image will be fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    setupZoom(config)
                    if (isVisibleOnScreen()) landscapeZoom(true)
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError(e)
                }
            },
        )

        when (data) {
            is BitmapDrawable -> {
                setImage(ImageSource.bitmap(data.bitmap))
                isVisible = true
            }
            // MihonSY: when enhancement is on, decode through Coil so the enhancer
            // runs inside the decoder (on a background thread, at higher resolution).
            // When enhancement is off, keep the original SSIV direct-decode path.
            // Enhancement now applies to every reading mode (webtoon/strip included).
            is BufferedSource -> {
                val preferences = Injekt.get<ReaderPreferences>()
                // Komiho: E-Ink 灰阶化也必须走这条 Coil / 自定义解码器链 ——
                // 下面那条 SSIV 直解路径完全绕过位图处理，灰阶化会形同虚设。
                // 这与「必须强制 customDecoder」是同一个原因。
                val einkGrayOn = Injekt.get<UiPreferences>().isEinkGrayscaleActive
                // MihonSY: only enhance streams that actually look like a standard
                // image (JPEG/PNG/WebP/GIF magic). Downloaded chapters packed as CBZ
                // (encrypted or raw archives) can yield non-image streams; feeding
                // those to the enhancement decoder crashed the reader. Non-standard
                // streams fall back to the original SSIV direct-decode path.
                // Komiho: 深度裁剪边缘开启时也必须走这条链 —— SSIV 直解路径带不上
                // aggressiveCrop 参数（它内部按纯白边算法裁边），会退回标准裁切。
                val enhancementOn = (
                    preferences.enhancementMode.get() != 0 ||
                        einkGrayOn ||
                        config.cropBordersAggressive
                    ) &&
                    isStandardImageStream(data)
                if (!enhancementOn) {
                    setHardwareConfig(ImageUtil.canUseHardwareBitmap(data))
                    setImage(ImageSource.inputStream(data.inputStream()))
                    isVisible = true
                    return@apply
                }

                val startTime = android.os.SystemClock.uptimeMillis()
                ImageRequest.Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    // Komiho: enhanced 只表达「真增强」。E-Ink 灰阶化开启时也要走这条链，
                    // 但不能因此把解码目标抬到 2048 再回缩 —— 那是白跑一趟高分辨率解码。
                    .enhanced(preferences.enhancementMode.get() != 0 && !this@ReaderPageImageView.skipEnhance)
                    .customDecoder(true)
                    // Komiho: webtoon 原始尺寸模式（1:1 显示）不下调 AI 面积回缩 ——
                    // 没有缩小就没有摩尔纹，回缩只会丢细节。
                    .originalSizeDisplay(config.minimumScaleType == SubsamplingScaleImageView.SCALE_TYPE_ORIGINAL_SIZE)
                    // Komiho 诊断：带上页号（prewarm 保持默认 false → 日志里显示 holder#N）。
                    .pageIndex(this@ReaderPageImageView.pageIndex)
                    .target(
                        onSuccess = { result ->
                            val image = result as BitmapImage
                            setImage(ImageSource.bitmap(image.bitmap))
                            isVisible = true
                            // Komiho: webtoon 条目是 WRAP_CONTENT，NPU 增强是同步解码，
                            // 常在 holder 还离屏（高度已量到 0）时就跑完。若此刻不把视图高度
                            // 定下来，增强图会落进 0 高度 SSIV，要等下次 RV 布局（滚进可视区）
                            // 才撑开 —— 表现就是「滚到才显示」。这里按 bitmap 宽高比预置真实
                            // 高度并立即 requestLayout，让增强图一算完就显示。
                            if (this@ReaderPageImageView.isWebtoon) {
                                val bmp = image.bitmap
                                if (bmp.width > 0) {
                                    val viewWidth = this@ReaderPageImageView.width.takeIf { it > 0 }
                                        ?: this@ReaderPageImageView.context.resources.displayMetrics.widthPixels
                                    val computedHeight = (bmp.height * viewWidth / bmp.width.toFloat()).toInt()
                                    this@ReaderPageImageView.layoutParams?.height = computedHeight
                                    this@ReaderPageImageView.requestLayout()
                                }
                            }
                            // Komiho: 尺寸门 / HARDWARE / 输入已回收这些「什么都没做」的情况由增强器
                            // 登记成 skip。角标必须显示 skip —— 否则会落回 engineLabel 的默认值
                            // `CPU OK`，看着像 CPU 做过增强（实测 1600×10064 这类大图就是如此）。
                            // DSH: 取未增强的原因（不只是布尔）—— 只写日志，画面上不再出现「跳过」
                            val skipReason = EnhanceTimings.takeSkipped(pageIndex)
                            val skipped = skipReason != null
                            // Komiho (2026-10-13): 计时只取一次——既做角标耗时，也判「真增强过」。
                            val timing = EnhanceTimings.take(pageIndex)
                            // Komiho (2026-10-13): 被抢占打断（快速翻页 / 预热抢占）的页：
                            // 无成功计时、无 skip 标记，但原生有打断记录。角标不能冒充引擎
                            // OK + 墙钟假耗时，继续保持「增强中…」；holder 也据此在停顿后补算一次。
                            val aborted = !skipped && timing == null && Waifu2x.wasAborted(pageIndex)
                            // DSH: 只有「真增强」才算这一页完成。以前把 skip 也算完成，
                            // 于是被 skip 的页永远不会再补算 —— 用户实测：「一显示跳过，超分就停了」。
                            currentImageEnhanced = timing != null
                            onDecodeCompleted?.invoke(currentImageEnhanced, aborted)
                            showEnhancementOutcome(
                                success = !skipped && !aborted,
                                // DSH: 原因只进日志；画面上不再出现「跳过」/「等待重试」这类字样，
                                // 未出引擎结果就是「增强中…」。
                                reason = when {
                                    skipReason != null -> skipReason.label
                                    aborted -> "被抢占，等停顿补算"
                                    else -> null
                                },
                                // Komiho: 优先用解码器登记的实际计算耗时（解码 + 增强，已剔除等锁）；
                                // 取不到才回退 Coil 外层墙钟（含排队会虚高）。
                                elapsedMillis = when {
                                    skipped -> 0L
                                    timing != null -> timing
                                    else -> android.os.SystemClock.uptimeMillis() - startTime
                                },
                                output = "${image.bitmap.width}×${image.bitmap.height}",
                            )
                        },
                    )
                    .listener(
                        onError = { _, _ ->
                            onImageLoadError(null)
                            // Komiho (2026-10-13): 失败也要回执——否则 holder 的补算在飞标记
                            // 永远不释放，之后真需要补算时会被自己挡住。
                            currentImageEnhanced = false
                            onDecodeCompleted?.invoke(false, false)
                            // DSH: 加载失败时错误界面自己会说明情况，别在上面再挂一个「增强中…」。
                            hideEnhancementStatus()
                        },
                    )
                    .size(ViewSizeResolver(this@ReaderPageImageView))
                    .precision(Precision.INEXACT)
                    .cropBorders(config.cropBorders)
                    .cropBordersAggressive(config.cropBordersAggressive)
                    .crossfade(false)
                    .build()
                    .also {
                        // DSH: 现场解码路径（源交给 Coil 在 view 里再解一次）也在请求时点亮提示。
                        // 真正耗时的预解码路径由 PagerPageHolder 在开始准备时就点亮，
                        // 两处共用同一个角标，不存在两个提示源打架。
                        showEnhancementPending()
                    }
                    .let(context.imageLoader::enqueue)
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force 2 scale levels on double tap
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged(scale)
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? AppCompatImageView)?.apply {
        if (this is PhotoView) {
            setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
        }

        val request = ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(context.resources)
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onImageLoaded()
                },
            )
            .listener(
                onError = { _, result ->
                    onImageLoadError(result.throwable)
                },
            )
            .crossfade(false)
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun Int.getSystemScaledDuration(): Int {
        // Komiho: E-Ink 下取 **1ms**（= 阅读器「双击动画时长」里那个"无动画"档位），
        // **不能给 0** —— 这条时长会喂给 SSIV 的双击缩放动画，而它的缓动是手写的：
        // `easeInOutQuad(time, from, change, duration)` 里 `timeF = time / (duration / 2f)`，
        // duration=0 时 `0 / 0f` = NaN → `scale` 被写成 NaN（图画不出来、缩放失效），
        // 而且 `finished = scaleElapsed > 0` 会立刻判成动画已完成、把动画对象丢掉，
        // 之后再双击也回不到正常值，只能换页 / 重开阅读器重建视图（真机上复现）。
        // 1ms 时首帧取起始值（画面与当前完全一致）、次帧直接到终值，观感等同瞬时。
        // 这里同时覆盖 PhotoView 的 `setZoomTransitionDuration`。
        if (EinkMotion.isAnimationOff) return 1
        return (this * context.animatorDurationScale).toInt().coerceAtLeast(1)
    }

    /**
     * All of the config except [zoomDuration] will only be used for non-animated image.
     */
    data class Config(
        val zoomDuration: Int,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        // Komiho: 深度裁剪边缘；单独为 true 无效果，解码器按 cropBorders && cropBordersAggressive 消费。
        val cropBordersAggressive: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }
}

private const val MAX_ZOOM_SCALE = 5F

/**
 * MihonSY: true when the stream begins with a known image magic number
 * (JPEG/PNG/WebP/GIF). Downloaded CBZ-packed chapters can hand the reader a
 * non-image stream (encrypted/raw archive data); the enhancement decoder
 * crashes on those, so they must skip enhancement and use the standard path.
 * [BufferedSource.peek] does not consume the stream.
 */
internal fun isStandardImageStream(source: BufferedSource): Boolean {
    return try {
        source.peek().use { peek ->
            val head = peek.readByteArray(16)
            (head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()) || // JPEG
                (head.size >= 8 && head[0] == 0x89.toByte() && head[1] == 0x50.toByte() && head[2] == 0x4E.toByte() && head[3] == 0x47.toByte() && head[4] == 0x0D.toByte() && head[5] == 0x0A.toByte() && head[6] == 0x1A.toByte() && head[7] == 0x0A.toByte()) || // PNG
                (head.size >= 12 && head[0] == 0x52.toByte() && head[1] == 0x49.toByte() && head[2] == 0x46.toByte() && head[3] == 0x46.toByte() && head[8] == 0x57.toByte() && head[9] == 0x45.toByte() && head[10] == 0x42.toByte() && head[11] == 0x50.toByte()) || // WEBP
                (head.size >= 6 && head[0] == 0x47.toByte() && head[1] == 0x49.toByte() && head[2] == 0x46.toByte() && head[3] == 0x38.toByte()) // GIF
        }
    } catch (e: Exception) {
        false
    }
}
