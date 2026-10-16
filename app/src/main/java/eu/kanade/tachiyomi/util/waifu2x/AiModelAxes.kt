package eu.kanade.tachiyomi.util.waifu2x

/**
 * DSH (2026-10-16): 插件模型「一条一变体」→ komikku_img_upscale 的四轴视图。
 *
 * komikku 把权重全打进 APP，所以能按「模型族 / 降噪等级 / 放大倍数 / 精度」四个轴自由组合；
 * Komiho 走的是插件包，每个具体变体各自成条（`realcugan-pro-x3-denoise3x-int8`）。这里把那套轴
 * 映射回**包内实际存在的条目**：有的就点亮、没有的自动回退并把轴纠正到可用值（对齐 komikku
 * `ColorFilterPage` 里那几段 `LaunchedEffect` 校验），而真正的模型 id 仍然写进
 * [eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.aiModelId] —— 增强链路完全不变。
 *
 * 下标取值与 komikku 一致（降噪 0/1/2/3/4，精度 0=FP16、2=INT8），方便日后对照它的界面。
 */
object AiModelAxes {

    /** 降噪等级下标（与 komikku 对齐）：0 无 / 1 1x / 2 2x / 3 3x / 4 保守。 */
    const val NOISE_NONE = 0
    const val NOISE_1X = 1
    const val NOISE_2X = 2
    const val NOISE_3X = 3
    const val NOISE_CONSERVATIVE = 4

    /** 精度下标（与 komikku 对齐）：0 FP16 / 2 INT8。 */
    const val PRECISION_FP16 = 0
    const val PRECISION_INT8 = 2

    /** 一组轴：族 + 降噪 + 精度。倍率直接用 manifest 的 [PluginUpscaleModel.scale]。 */
    data class Axes(val family: String, val noise: Int, val precision: Int)

    data class Match(val model: PluginUpscaleModel, val axes: Axes)

    private val NOISE_SUFFIXES = listOf(
        "-no-denoise" to NOISE_NONE,
        "-denoise1x" to NOISE_1X,
        "-denoise2x" to NOISE_2X,
        "-denoise3x" to NOISE_3X,
        "-conservative" to NOISE_CONSERVATIVE,
    )

    /**
     * 从 stem 解析出「族 / 降噪 / 精度」。
     *
     * 解析顺序自右向左：先摘精度后缀（`-int8` / `-fp16`，无后缀按 FP16 处理，如
     * `waifu2x-cunet-anime-noise2-x2`），再摘降噪后缀，最后摘倍率 token `-x<digits>`
     * —— `realesrgan-general-x4v3-x2-int8` 里的 `x4v3` 不在末尾，不会被误摘。
     */
    fun parse(stemRaw: String): Axes {
        var s = stemRaw.removePrefix("qnn-")
        val precision = if (s.endsWith("-int8")) PRECISION_INT8 else PRECISION_FP16
        if (s.endsWith("-int8")) s = s.removeSuffix("-int8")
        var noise = NOISE_NONE
        for ((suffix, level) in NOISE_SUFFIXES) {
            if (s.endsWith(suffix)) {
                noise = level
                s = s.removeSuffix(suffix)
                break
            }
        }
        s = Regex("-x\\d+$").replace(s, "")
        return Axes(family = s, noise = noise, precision = precision)
    }

    fun familyLabel(family: String): String = when (family) {
        "realcugan-pro" -> "Real-CUGAN Pro"
        "realcugan-se" -> "Real-CUGAN SE"
        "realesrgan-general-x4v3" -> "Real-ESRGAN"
        "w2xex-photo-small" -> "W2xEX Photo Small"
        "nomosuni-span" -> "SPAN NomosUni Photo"
        "universal-fast-v2" -> "W2xEX Universal Fast"
        "w2xex-omni-smallv2" -> "W2xEX Omni Small"
        "w2xex-omni-turbo-v15" -> "W2xEX Omni Turbo"
        else -> family
    }

    fun noiseLabel(noise: Int): String = when (noise) {
        NOISE_NONE -> "无"
        NOISE_1X -> "1x"
        NOISE_2X -> "2x"
        NOISE_3X -> "3x"
        NOISE_CONSERVATIVE -> "保守"
        else -> "?"
    }

    fun precisionLabel(precision: Int): String =
        if (precision == PRECISION_INT8) "INT8" else "FP16"

    /** 只有 Real-CUGAN 族带降噪变体（W2xEX / SPAN / Real-ESRGAN 没有降噪档）。 */
    fun hasNoiseAxis(family: String): Boolean = family.startsWith("realcugan")

    /** 包内出现过的族（保持 manifest 顺序去重）。 */
    fun families(models: List<PluginUpscaleModel>): List<String> =
        models.map { parse(it.stem).family }.distinct()

    fun scalesOf(models: List<PluginUpscaleModel>, family: String): List<Int> =
        models.filter { parse(it.stem).family == family }
            .map { it.scale }
            .distinct()
            .sorted()

    fun noisesOf(models: List<PluginUpscaleModel>, family: String, scale: Int): List<Int> =
        models.filter { parse(it.stem).family == family && it.scale == scale }
            .map { parse(it.stem).noise }
            .distinct()
            .sorted()

    fun precisionsOf(
        models: List<PluginUpscaleModel>,
        family: String,
        scale: Int,
        noise: Int,
    ): List<Int> =
        models.filter {
            val a = parse(it.stem)
            a.family == family && it.scale == scale && a.noise == noise
        }.map { parse(it.stem).precision }.distinct().sorted()

    /**
     * 把「想要的轴」解析成包内实际可用的模型：
     * 精确匹配优先；否则**同族内**按 倍率 → 降噪 → 精度 逐项放宽（同族一个都没有才跨族），
     * 返回的 [Match.axes] 是放宽后的真实取值 —— 调用方据此回写偏好，等同于 komikku 的自动纠正。
     */
    fun resolve(
        models: List<PluginUpscaleModel>,
        want: Axes,
        wantScale: Int,
    ): Match? {
        if (models.isEmpty()) return null
        val parsed = models.map { it to parse(it.stem) }
        parsed.firstOrNull { (m, a) ->
            a.family == want.family && m.scale == wantScale &&
                a.noise == want.noise && a.precision == want.precision
        }?.let { return Match(it.first, it.second) }

        val sameFamily = parsed.filter { it.second.family == want.family }
        val pool = sameFamily.ifEmpty { parsed }
        val best = pool.minWithOrNull(
            compareBy(
                { if (it.first.scale == wantScale) 0 else 1 },
                { if (it.second.noise == want.noise) 0 else 1 },
                { if (it.second.precision == want.precision) 0 else 1 },
                { it.first.scale },
            ),
        ) ?: return null
        return Match(best.first, best.second)
    }
}
