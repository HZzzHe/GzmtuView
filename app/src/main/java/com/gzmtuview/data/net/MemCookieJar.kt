package com.gzmtuview.data.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 内存 Cookie 会话存储（单域教务系统足够）。
 * 不持久化到磁盘：会话过期由保存凭据自动重登（与 Flutter 版策略一致）。
 *
 * 实现要点：按「cookie 名合并 + 过期过滤 + 域匹配」，不能整存整取。
 * OkHttp 对任何响应都会调用 saveFromResponse——若响应不带 Set-Cookie，
 * 传入的是空列表；整存整取会把已保存的 JSESSIONID 清空，导致登录 POST
 * 不带会话 Cookie 而被服务器拒绝（返回登录页 → "验证码"字样误报）。
 */
class MemCookieJar : CookieJar {
    private val map = ConcurrentHashMap<String, CopyOnWriteArrayList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val store = map.computeIfAbsent(url.host) { CopyOnWriteArrayList() }
        val now = System.currentTimeMillis()
        for (c in cookies) {
            // 同名 cookie 先移除旧值再追加（覆盖更新 / 处理服务器删除）
            store.removeAll { it.name == c.name }
            if (c.expiresAt > now) {
                store.add(c)
            }
        }
        // 清理已过期项
        store.removeAll { it.expiresAt <= now }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val store = map[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return store.filter { it.expiresAt > now && it.matches(url) }
    }

    fun clear() {
        map.clear()
    }
}
