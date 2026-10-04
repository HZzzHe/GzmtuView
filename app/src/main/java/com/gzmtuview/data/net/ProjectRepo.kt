package com.gzmtuview.data.net

/**
 * 发布方仓库信息。
 *
 * ★ 开源版留空，发布前把 [SLUG] 填成你自己的 `<owner>/<repo>`（形如 `your-name/gzmtu-view`）。
 * 留空即「还没有公开仓库」：设置页不出现「项目主页」一行，「检查更新」直接判定为无信息，
 * 不会去打 GitHub API，也不会指向任何人的仓库。
 */
object ProjectRepo {

    const val SLUG = ""

    /** 仓库主页地址；[SLUG] 为空时返回 null。 */
    val homeUrl: String? get() = if (SLUG.isBlank()) null else "https://github.com/$SLUG"

    /** GitHub Releases 最新版本的 API 地址；[SLUG] 为空时返回 null。 */
    val latestReleaseApi: String?
        get() = if (SLUG.isBlank()) null else "https://api.github.com/repos/$SLUG/releases/latest"
}
