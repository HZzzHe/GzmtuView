package com.gzmtuview.data.net

import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher

/**
 * 正方教务登录所需的 RSA 密码加密。
 *
 * 与正方前端 login.js 完全一致：
 * modulus/exponent 为 Base64 编码，构造 RSA 公钥，
 * 使用 PKCS#1 v1.5 填充加密（注意：不是 OAEP），
 * 密文再转 Base64 提交。
 */
object RsaEncryptor {
    fun encrypt(plainText: String, modulusB64: String, exponentB64: String): String {
        val modulus = BigInteger(1, Base64.decode(modulusB64, Base64.DEFAULT))
        val exponent = BigInteger(1, Base64.decode(exponentB64, Base64.DEFAULT))

        val keyFactory = KeyFactory.getInstance("RSA")
        val pubKey = keyFactory.generatePublic(
            RSAPublicKeySpec(modulus, exponent)
        ) as RSAPublicKey

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pubKey)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
}
